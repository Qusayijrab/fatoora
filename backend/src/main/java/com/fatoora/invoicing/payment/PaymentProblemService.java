package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Online payments the provider took but we could not apply to an invoice: it was already
 * paid or voided, or the amount was more than the balance (for example a second checkout
 * completed after a manual payment). The client has been charged, so these stay listed
 * until someone refunds them in the provider's dashboard and marks them handled.
 */
@Service
public class PaymentProblemService {

    public record PaymentProblem(
            UUID id,
            String provider,
            UUID invoiceId,
            String invoiceNumber,
            String clientName,
            String detail,
            Instant receivedAt) {}

    private static final String UNRESOLVED = """
            w.organization_id = ? AND w.invoice_id IS NOT NULL
            AND w.status IN ('FAILED', 'IGNORED') AND w.resolved_at IS NULL""";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public PaymentProblemService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PaymentProblem> unresolved() {
        UUID org = TenantContext.require().organizationId();
        return jdbc.query("""
                SELECT w.id, w.provider, w.invoice_id, i.number, COALESCE(i.bill_to_name, c.name), w.detail, w.received_at
                FROM webhook_events w
                JOIN invoices i ON i.id = w.invoice_id AND i.organization_id = w.organization_id
                JOIN clients c ON c.id = i.client_id
                """ + "WHERE " + UNRESOLVED + " ORDER BY w.received_at",
                (rs, n) -> new PaymentProblem(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant()),
                org);
    }

    /** Marks a problem handled (typically: the payment was refunded in the provider's dashboard). */
    @Transactional
    public void resolve(UUID id) {
        CurrentTenant tenant = TenantContext.require().requireRole(Role.ADMIN);
        UUID invoiceId = jdbc.query("SELECT w.invoice_id FROM webhook_events w WHERE w.id = ? AND " + UNRESOLVED,
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, id, tenant.organizationId());
        if (invoiceId == null) {
            throw ApiException.notFound("Payment problem not found");
        }
        jdbc.update("UPDATE webhook_events SET resolved_at = now(), resolved_by = ? WHERE id = ?", tenant.userId(), id);
        audit.record("invoice", invoiceId, "online_payment_problem_resolved",
                "Unapplied online payment marked as handled", Map.of("webhookEvent", id.toString()));
    }
}
