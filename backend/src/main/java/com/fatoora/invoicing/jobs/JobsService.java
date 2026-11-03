package com.fatoora.invoicing.jobs;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.common.tenancy.TenantRunner;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.mail.DocumentEmailService;
import com.fatoora.invoicing.mail.DocumentEmailService.EmailRequest;
import com.fatoora.invoicing.quote.QuoteRepository;
import com.fatoora.invoicing.recurring.RecurringService;
import com.fatoora.invoicing.recurring.RecurringService.Generated;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The work behind the scheduled jobs, with "now" passed in so tests can travel in time.
 *
 * <p>Pattern for every job: one cross-organization SQL query finds candidates (using each
 * organization's own time zone to decide what "today" is), then each item is processed
 * inside {@link TenantRunner}: its own transaction, scoped to its organization. One
 * failing item never blocks the others, and every step is safe to repeat.
 */
@Service
public class JobsService {

    private static final Logger log = LoggerFactory.getLogger(JobsService.class);

    /** Catch-up bound per template per run (e.g. after the server was down for months). */
    private static final int MAX_PERIODS_PER_RUN = 12;

    private final JdbcTemplate jdbc;
    private final TenantRunner runner;
    private final RecurringService recurring;
    private final InvoiceRepository invoices;
    private final QuoteRepository quotes;
    private final DocumentEmailService emails;
    private final ReminderService reminders;
    private final AuditService audit;
    private final Clock clock;

    public JobsService(JdbcTemplate jdbc, TenantRunner runner, RecurringService recurring, InvoiceRepository invoices,
            QuoteRepository quotes, DocumentEmailService emails, ReminderService reminders, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.runner = runner;
        this.recurring = recurring;
        this.invoices = invoices;
        this.quotes = quotes;
        this.emails = emails;
        this.reminders = reminders;
        this.audit = audit;
        this.clock = clock;
    }

    private record Target(UUID organizationId, UUID id) {}

    // ---- recurring invoices ----

    /** Generates every due period of every active template. Returns how many invoices were created. */
    public int runRecurring(Instant now) {
        List<Target> due = jdbc.query("""
                SELECT t.organization_id, t.id FROM recurring_templates t
                JOIN organizations o ON o.id = t.organization_id
                WHERE t.status = 'ACTIVE' AND t.next_run_date <= (?::timestamptz AT TIME ZONE o.time_zone)::date
                """, (rs, n) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), ts(now));
        int created = 0;
        for (Target t : due) {
            for (int i = 0; i < MAX_PERIODS_PER_RUN; i++) {
                Generated g;
                try {
                    g = runner.inOrganization(t.organizationId(), () -> recurring.generateNext(t.id(), now, false));
                } catch (RuntimeException e) {
                    log.error("Recurring template {} failed", t.id(), e);
                    break;
                }
                if (g == null) {
                    break;
                }
                created++;
                if (g.email()) {
                    emailQuietly(t.organizationId(), g.invoiceId());
                }
            }
        }
        return created;
    }

    /** "Generate now" from the UI: the current period, even if its date is in the future. */
    public UUID generateNow(UUID templateId) {
        CurrentTenant tenant = TenantContext.require();
        Generated g = recurring.generateNext(templateId, clock.instant(), true);
        if (g != null && g.email()) {
            emailQuietly(tenant.organizationId(), g.invoiceId());
        }
        return g == null ? null : g.invoiceId();
    }

    private void emailQuietly(UUID organizationId, UUID invoiceId) {
        CurrentTenant previous = TenantContext.current().orElse(null);
        TenantContext.set(new CurrentTenant(organizationId, null, com.fatoora.invoicing.organization.Role.OWNER));
        try {
            emails.emailInvoice(invoiceId, new EmailRequest(null, null));
        } catch (RuntimeException e) {
            log.warn("Could not email recurring invoice {}: {}", invoiceId, e.getMessage());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    // ---- daily statuses ----

    /** ISSUED / PARTIALLY_PAID invoices past their due date (in the org's time zone) become OVERDUE. */
    public int markOverdue(Instant now) {
        List<Target> late = jdbc.query("""
                SELECT i.organization_id, i.id FROM invoices i
                JOIN organizations o ON o.id = i.organization_id
                WHERE i.kind = 'INVOICE' AND i.status IN ('ISSUED', 'PARTIALLY_PAID')
                  AND i.due_date < (?::timestamptz AT TIME ZONE o.time_zone)::date
                """, (rs, n) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), ts(now));
        int changed = 0;
        for (Target t : late) {
            Boolean done = runner.inOrganization(t.organizationId(), () -> {
                Invoice invoice = invoices.findForUpdate(t.id()).orElse(null);
                if (invoice == null) {
                    return false;
                }
                LocalDate today = today(t.organizationId(), now);
                if (invoice.markOverdueIfLate(today)) {
                    audit.record(t.organizationId(), null, "invoice", invoice.getId(), "overdue",
                            "Invoice " + invoice.getNumber() + " is overdue",
                            Map.of("dueDate", String.valueOf(invoice.getDueDate())));
                    return true;
                }
                return false;
            });
            if (Boolean.TRUE.equals(done)) {
                changed++;
            }
        }
        return changed;
    }

    /** SENT quotes past their "valid until" date become EXPIRED. */
    public int expireQuotes(Instant now) {
        List<Target> stale = jdbc.query("""
                SELECT q.organization_id, q.id FROM quotes q
                JOIN organizations o ON o.id = q.organization_id
                WHERE q.status = 'SENT' AND q.valid_until < (?::timestamptz AT TIME ZONE o.time_zone)::date
                """, (rs, n) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), ts(now));
        int changed = 0;
        for (Target t : stale) {
            Boolean done = runner.inOrganization(t.organizationId(), () -> quotes.findForUpdate(t.id())
                    .map(q -> {
                        boolean expired = q.expireIfLate(today(t.organizationId(), now));
                        if (expired) {
                            audit.record(t.organizationId(), null, "quote", q.getId(), "expired",
                                    "Quote " + q.getNumber() + " expired", Map.of());
                        }
                        return expired;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(done)) {
                changed++;
            }
        }
        return changed;
    }

    // ---- reminders ----

    /**
     * For each overdue invoice with a client email, the latest reminder day it has reached
     * (e.g. 7 of {1, 7, 14}) is sent once. Earlier missed days are skipped, so a client
     * never gets three reminders in one morning after downtime.
     */
    public int sendReminders(Instant now) {
        List<ReminderService.Due> due = jdbc.query("""
                SELECT i.organization_id, i.id, MAX(d) AS day
                FROM invoices i
                JOIN organizations o ON o.id = i.organization_id
                CROSS JOIN LATERAL unnest(o.reminder_days) AS d
                WHERE o.reminders_enabled AND i.kind = 'INVOICE' AND i.status = 'OVERDUE'
                  AND i.bill_to_email IS NOT NULL
                  AND ((?::timestamptz AT TIME ZONE o.time_zone)::date - i.due_date) >= d
                GROUP BY i.organization_id, i.id
                HAVING NOT EXISTS (SELECT 1 FROM invoice_reminders r WHERE r.invoice_id = i.id AND r.days_overdue = MAX(d))
                """, (rs, n) -> new ReminderService.Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3)), ts(now));
        int sent = 0;
        for (ReminderService.Due d : due) {
            if (reminders.send(d, now)) {
                sent++;
            }
        }
        return sent;
    }

    private LocalDate today(UUID organizationId, Instant now) {
        String zone = jdbc.queryForObject("SELECT time_zone FROM organizations WHERE id = ?", String.class, organizationId);
        return LocalDate.ofInstant(now, ZoneId.of(zone));
    }

    /** JDBC can't bind an Instant directly; a UTC OffsetDateTime maps to timestamptz. */
    private static java.time.OffsetDateTime ts(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC);
    }
}
