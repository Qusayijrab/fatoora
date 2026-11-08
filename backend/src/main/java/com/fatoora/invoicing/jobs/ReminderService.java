package com.fatoora.invoicing.jobs;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.tenancy.TenantRunner;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceService;
import com.fatoora.invoicing.mail.ClientEmailPolicy;
import com.fatoora.invoicing.mail.MailService;
import com.fatoora.invoicing.mail.MailService.Attachment;
import com.fatoora.invoicing.mail.MailService.OutgoingMail;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.payment.PaymentLinkService;
import com.fatoora.invoicing.pdf.DocumentPdfService;
import com.fatoora.invoicing.pdf.DocumentView;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Sends one overdue reminder, exactly once:
 * <ol>
 *   <li>claim it: INSERT (invoice, day) ON CONFLICT DO NOTHING, and build the email, in
 *       one short transaction;</li>
 *   <li>send it, outside any transaction;</li>
 *   <li>if sending failed, delete the claim so the next run retries; if it worked, audit it.</li>
 * </ol>
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    public record Due(UUID organizationId, UUID invoiceId, int daysOverdue) {}

    private final TenantRunner runner;
    private final InvoiceService invoices;
    private final OrganizationService organizations;
    private final DocumentPdfService pdfs;
    private final PaymentLinkService payLinks;
    private final MailService mail;
    private final ClientEmailPolicy clientEmails;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public ReminderService(TenantRunner runner, InvoiceService invoices, OrganizationService organizations,
            DocumentPdfService pdfs, PaymentLinkService payLinks, MailService mail, AuditService audit, JdbcTemplate jdbc,
            ClientEmailPolicy clientEmails) {
        this.clientEmails = clientEmails;
        this.runner = runner;
        this.invoices = invoices;
        this.organizations = organizations;
        this.pdfs = pdfs;
        this.payLinks = payLinks;
        this.mail = mail;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public boolean send(Due due, Instant now) {
        if (!clientEmails.canEmailClients(due.organizationId())) {
            return false; // nothing claimed: it goes out on a later run once an owner confirms their email
        }
        UUID claimId = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        OutgoingMail outgoing = runner.inOrganization(due.organizationId(), () -> {
            Invoice invoice = invoices.loadForUpdate(due.invoiceId());
            String to = invoice.getBillTo() == null ? null : invoice.getBillTo().getEmail();
            if (to == null || !invoice.getStatus().isOpen()) {
                return null;
            }
            int claimed = jdbc.update("""
                    INSERT INTO invoice_reminders (id, organization_id, invoice_id, days_overdue, sent_to)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (invoice_id, days_overdue) DO NOTHING
                    """, claimId, due.organizationId(), invoice.getId(), due.daysOverdue(), to);
            if (claimed == 0) {
                return null;
            }
            Organization org = organizations.currentEntity();
            var pdf = pdfs.invoicePdf(invoice, org);
            Map<String, Object> model = new HashMap<>();
            model.put("orgName", org.getName());
            model.put("orgEmail", org.getEmail());
            model.put("clientName", invoice.displayClientName());
            model.put("number", invoice.getNumber());
            model.put("balance", DocumentView.money(invoice.balanceDue()));
            model.put("dueDate", DocumentView.date(invoice.getDueDate()));
            model.put("daysOverdue", due.daysOverdue());
            model.put("payUrl", payLinks.payUrlFor(invoice));
            return new OutgoingMail(to, org.getEmail(), org.getName(),
                    "Reminder: invoice " + invoice.getNumber() + " is overdue", "reminder", model,
                    new Attachment(pdf.filename(), pdf.content(), "application/pdf"));
        });
        if (outgoing == null) {
            return false;
        }
        try {
            mail.send(outgoing);
        } catch (RuntimeException e) {
            log.warn("Reminder for invoice {} failed; will retry next run: {}", due.invoiceId(), e.getMessage());
            jdbc.update("DELETE FROM invoice_reminders WHERE id = ?", claimId);
            return false;
        }
        runner.inOrganization(due.organizationId(), () -> audit.record(due.organizationId(), null, "invoice",
                due.invoiceId(), "reminder_sent", "Reminder sent to " + outgoing.to() + " (" + due.daysOverdue()
                        + (due.daysOverdue() == 1 ? " day" : " days") + " overdue)", Map.of("to", outgoing.to(), "daysOverdue", due.daysOverdue())));
        return true;
    }
}
