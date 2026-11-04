package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantRunner;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceService;
import com.fatoora.invoicing.invoice.Payment;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import com.fatoora.invoicing.payment.PaymentGateway.WebhookEvent;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Turns provider webhooks into payments (CLAUDE.md rule 8):
 * <ol>
 *   <li>verify the signature (else 400, nothing stored);</li>
 *   <li>record the event by the provider's event id; a duplicate delivery hits the
 *       unique constraint and is acknowledged without doing anything;</li>
 *   <li>apply the payment to the invoice, in the same transaction as step 2. If anything
 *       unexpected fails, both roll back and the provider's retry starts clean.</li>
 * </ol>
 * Business problems (invoice already paid or voided, amount over the balance) do not
 * throw: the event is stored as IGNORED or FAILED with its invoice, listed by
 * {@link PaymentProblemService} until someone marks it handled, and written to the
 * invoice's history. The provider gets a 200 so it stops retrying something a retry
 * cannot fix.
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome {
        PROCESSED,
        DUPLICATE,
        IGNORED,
        FAILED
    }

    private final PaymentGateway gateway;
    private final CheckoutSessionRepository sessions;
    private final InvoiceService invoices;
    private final OrganizationService organizations;
    private final TenantRunner runner;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public WebhookService(PaymentGateway gateway, CheckoutSessionRepository sessions, InvoiceService invoices,
            OrganizationService organizations, TenantRunner runner, JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.gateway = gateway;
        this.sessions = sessions;
        this.invoices = invoices;
        this.organizations = organizations;
        this.runner = runner;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    public Outcome handle(String provider, String payload, Map<String, String> headers) {
        if (!gateway.name().equals(provider)) {
            throw ApiException.notFound("Unknown payment provider");
        }
        WebhookEvent event;
        try {
            event = gateway.parseWebhook(payload, headers);
        } catch (InvalidWebhookException e) {
            log.warn("Rejected {} webhook: {}", provider, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        if (!event.completed()) {
            boolean stored = insertIfNew(provider, event, null, "IGNORED", "Event type not handled", payload);
            return stored ? Outcome.IGNORED : Outcome.DUPLICATE;
        }

        CheckoutSession session = sessions.findByProviderAndProviderSessionId(provider, event.providerSessionId())
                .orElse(null);
        if (session == null) {
            boolean stored = insertIfNew(provider, event, null, "FAILED", "Unknown checkout session", payload);
            return stored ? Outcome.FAILED : Outcome.DUPLICATE;
        }
        UUID orgId = session.getOrganizationId();
        if (event.organizationId() != null && !event.organizationId().equals(orgId)) {
            boolean stored = insertIfNew(provider, event, orgId, "FAILED", "Organization mismatch", payload);
            return stored ? Outcome.FAILED : Outcome.DUPLICATE;
        }

        return runner.inOrganization(orgId, () -> {
            if (!insertIfNew(provider, event, orgId, "RECEIVED", null, payload)) {
                return Outcome.DUPLICATE;
            }
            Invoice invoice = invoices.loadForUpdate(session.getInvoiceId());
            Money amount = event.amount();
            String problem = problemWith(invoice, amount);
            if (problem != null) {
                // Money was taken but cannot be applied: keep it in front of a human
                // (dashboard "needs attention" list and the invoice's history).
                mark(provider, event.eventId(), problem.startsWith("Ignored") ? "IGNORED" : "FAILED",
                        amount.toDisplayString() + " not applied. " + problem, invoice.getId());
                audit.record(orgId, null, "invoice", invoice.getId(), "online_payment_not_applied",
                        "Online payment of " + amount.toDisplayString() + " could not be applied to "
                                + invoice.getNumber() + ". Refund it in your " + provider + " dashboard.",
                        Map.of("amount", amount.toString(), "provider", provider, "reason", problem));
                log.warn("{} webhook {} not applied: {}", provider, event.eventId(), problem);
                return problem.startsWith("Ignored") ? Outcome.IGNORED : Outcome.FAILED;
            }
            invoices.applyPayment(invoice, amount, Payment.Method.ONLINE,
                    organizations.today(organizations.currentEntity(), clock),
                    "Online checkout " + event.providerSessionId(), null, provider, event.providerPaymentId());
            sessions.findByProviderAndProviderSessionId(provider, event.providerSessionId())
                    .ifPresent(s -> s.complete(clock.instant()));
            mark(provider, event.eventId(), "PROCESSED", "Paid " + amount + " on " + invoice.getNumber(), invoice.getId());
            return Outcome.PROCESSED;
        });
    }

    /** Checked up front, so applyPayment never throws inside the shared transaction. */
    private static String problemWith(Invoice invoice, Money amount) {
        if (!invoice.getStatus().isOpen()) {
            return "Ignored: invoice is " + invoice.getStatus().name().toLowerCase() + "; refund manually if money was taken";
        }
        if (!amount.currency().equals(invoice.getCurrency())) {
            return "Currency " + amount.currency() + " does not match the invoice (" + invoice.getCurrency() + ")";
        }
        if (!amount.isPositive() || amount.compareTo(invoice.balanceDue()) > 0) {
            return "Amount " + amount + " is more than the balance due " + invoice.balanceDue() + "; needs a refund";
        }
        return null;
    }

    private boolean insertIfNew(String provider, WebhookEvent e, UUID orgId, String status, String detail, String payload) {
        int rows = jdbc.update("""
                INSERT INTO webhook_events (id, provider, event_id, event_type, organization_id, status, detail, payload,
                                            processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CASE WHEN ? = 'RECEIVED' THEN NULL ELSE now() END)
                ON CONFLICT (provider, event_id) DO NOTHING
                """, UuidVersion7Strategy.INSTANCE.generateUuid(null), provider, e.eventId(), e.type(), orgId, status,
                detail, payload, status);
        return rows == 1;
    }

    private void mark(String provider, String eventId, String status, String detail, UUID invoiceId) {
        jdbc.update("""
                UPDATE webhook_events SET status = ?, detail = ?, invoice_id = ?, processed_at = now()
                WHERE provider = ? AND event_id = ?
                """, status, detail, invoiceId, provider, eventId);
    }
}
