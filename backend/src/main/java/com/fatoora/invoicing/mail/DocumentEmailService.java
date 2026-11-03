package com.fatoora.invoicing.mail;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.invoice.InvoiceStatus;
import com.fatoora.invoicing.mail.MailService.Attachment;
import com.fatoora.invoicing.mail.MailService.OutgoingMail;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.pdf.DocumentPdfService;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import com.fatoora.invoicing.pdf.DocumentView;
import com.fatoora.invoicing.quote.Quote;
import com.fatoora.invoicing.quote.QuoteRepository;
import com.fatoora.invoicing.quote.QuoteStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emails a document to its client with the PDF attached. Three steps, so a slow or failing
 * mail server never holds a database transaction open:
 * read and render (short read-only transaction) -> send -> record in the audit log.
 */
@Service
public class DocumentEmailService {

    public record EmailRequest(@Email @Size(max = 254) String to, @Size(max = 2000) String message) {}

    public record EmailResult(String sentTo) {}

    /** Builds the payment link for an invoice email, when online payment is available (Phase 5). */
    public interface PayLinkProvider extends Function<Invoice, String> {}

    private final InvoiceRepository invoices;
    private final QuoteRepository quotes;
    private final OrganizationService organizations;
    private final DocumentPdfService pdfs;
    private final MailService mail;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;
    private final java.util.Optional<PayLinkProvider> payLinks;

    public DocumentEmailService(InvoiceRepository invoices, QuoteRepository quotes, OrganizationService organizations,
            DocumentPdfService pdfs, MailService mail, AuditService audit, TransactionTemplate tx,
            java.util.Optional<PayLinkProvider> payLinks) {
        this.invoices = invoices;
        this.quotes = quotes;
        this.organizations = organizations;
        this.pdfs = pdfs;
        this.mail = mail;
        this.audit = audit;
        this.tx = tx;
        this.readTx = new TransactionTemplate(tx.getTransactionManager());
        this.readTx.setReadOnly(true);
        this.payLinks = payLinks;
    }

    public EmailResult emailInvoice(UUID id, EmailRequest request) {
        CurrentTenant tenant = TenantContext.require();
        // Read-write: composing the email may create the invoice's payment link.
        OutgoingMail outgoing = tx.execute(status -> {
            Invoice invoice = invoices.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
            if (invoice.getStatus() == InvoiceStatus.DRAFT || invoice.getStatus() == InvoiceStatus.VOID) {
                throw ApiException.conflict("Only issued invoices can be emailed. Issue it first.");
            }
            Organization org = organizations.currentEntity();
            String to = recipient(request, invoice.getBillTo() == null ? null : invoice.getBillTo().getEmail());
            PdfFile pdf = pdfs.invoicePdf(invoice, org);
            boolean creditNote = invoice.getKind() == InvoiceKind.CREDIT_NOTE;

            Map<String, Object> model = baseModel(org, invoice.displayClientName(), request.message());
            model.put("kind", creditNote ? "credit note" : "invoice");
            model.put("number", invoice.getNumber());
            model.put("total", DocumentView.money(invoice.getTotal()));
            model.put("balance", creditNote ? null : DocumentView.money(invoice.balanceDue()));
            model.put("dueDate", invoice.getDueDate() == null ? null : DocumentView.date(invoice.getDueDate()));
            model.put("paid", invoice.getStatus() == InvoiceStatus.PAID);
            model.put("payUrl", invoice.getStatus().isOpen() ? payLinks.map(p -> p.apply(invoice)).orElse(null) : null);

            String subject = (creditNote ? "Credit note " : "Invoice ") + invoice.getNumber() + " from " + org.getName();
            return new OutgoingMail(to, org.getEmail(), org.getName(), subject, "document", model,
                    new Attachment(pdf.filename(), pdf.content(), "application/pdf"));
        });

        mail.send(outgoing);
        tx.executeWithoutResult(s -> audit.record(tenant.organizationId(), tenant.userId(), "invoice", id, "emailed",
                "Emailed to " + outgoing.to(), Map.of("to", outgoing.to())));
        return new EmailResult(outgoing.to());
    }

    public EmailResult emailQuote(UUID id, EmailRequest request) {
        CurrentTenant tenant = TenantContext.require();
        OutgoingMail outgoing = readTx.execute(status -> {
            Quote quote = quotes.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("Quote not found"));
            if (quote.getStatus() == QuoteStatus.DRAFT) {
                throw ApiException.conflict("Send the quote first: drafts have no number yet.");
            }
            Organization org = organizations.currentEntity();
            String to = recipient(request, quote.getBillTo() == null ? null : quote.getBillTo().getEmail());
            PdfFile pdf = pdfs.quotePdf(quote, org);

            Map<String, Object> model = baseModel(org, quote.displayClientName(), request.message());
            model.put("kind", "quote");
            model.put("number", quote.getNumber());
            model.put("total", DocumentView.money(quote.getTotal()));
            model.put("validUntil", quote.getValidUntil() == null ? null : DocumentView.date(quote.getValidUntil()));
            return new OutgoingMail(to, org.getEmail(), org.getName(), "Quote " + quote.getNumber() + " from " + org.getName(),
                    "document", model, new Attachment(pdf.filename(), pdf.content(), "application/pdf"));
        });

        mail.send(outgoing);
        tx.executeWithoutResult(s -> audit.record(tenant.organizationId(), tenant.userId(), "quote", id, "emailed",
                "Emailed to " + outgoing.to(), Map.of("to", outgoing.to())));
        return new EmailResult(outgoing.to());
    }

    private static String recipient(EmailRequest request, String clientEmail) {
        String to = Strings.clean(request == null ? null : request.to());
        if (to == null) {
            to = clientEmail;
        }
        if (to == null) {
            throw ApiException.badRequest("This client has no email address. Add one to the client, or type a recipient.");
        }
        return to;
    }

    private static Map<String, Object> baseModel(Organization org, String clientName, String message) {
        Map<String, Object> model = new HashMap<>();
        model.put("orgName", org.getName());
        model.put("orgEmail", org.getEmail());
        model.put("clientName", clientName);
        model.put("message", Strings.clean(message));
        return model;
    }
}
