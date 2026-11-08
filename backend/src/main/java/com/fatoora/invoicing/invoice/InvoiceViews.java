package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.audit.AuditService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Records that the client opened an invoice (payment page, its PDF, or the portal). Plain
 * SQL on three columns the Invoice entity only reads, so it never races with a payment
 * or edit holding the row's entity. The first view is written to the invoice's history.
 */
@Service
public class InvoiceViews {

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final Clock clock;

    public InvoiceViews(JdbcTemplate jdbc, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    /** Call inside the invoice's organization (TenantRunner). Drafts are never "viewed". */
    public void record(UUID organizationId, UUID invoiceId) {
        Instant now = clock.instant();
        Boolean first = jdbc.query("""
                UPDATE invoices SET view_count = view_count + 1, last_viewed_at = ?,
                       first_viewed_at = COALESCE(first_viewed_at, ?)
                WHERE id = ? AND organization_id = ? AND status <> 'DRAFT'
                RETURNING view_count = 1
                """, rs -> rs.next() ? rs.getBoolean(1) : null, Timestamp.from(now), Timestamp.from(now), invoiceId,
                organizationId);
        if (Boolean.TRUE.equals(first)) {
            audit.record(organizationId, null, "invoice", invoiceId, "viewed", "The client opened the invoice", Map.of());
        }
    }
}
