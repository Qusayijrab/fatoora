package com.fatoora.invoicing.recurring;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.document.LineDraftFactory;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceService;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.recurring.RecurringDtos.RecurringRequest;
import com.fatoora.invoicing.recurring.RecurringDtos.RecurringResponse;
import com.fatoora.invoicing.recurring.RecurringDtos.RunResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringService {

    /** What one generation step produced (for the job: whether an email should follow). */
    public record Generated(UUID invoiceId, boolean issued, boolean email) {}

    private final RecurringTemplateRepository templates;
    private final ClientService clients;
    private final OrganizationService organizations;
    private final LineDraftFactory lineFactory;
    private final InvoiceService invoices;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RecurringService(RecurringTemplateRepository templates, ClientService clients, OrganizationService organizations,
            LineDraftFactory lineFactory, InvoiceService invoices, AuditService audit, JdbcTemplate jdbc, Clock clock) {
        this.templates = templates;
        this.clients = clients;
        this.organizations = organizations;
        this.lineFactory = lineFactory;
        this.invoices = invoices;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ---- CRUD ----

    @Transactional(readOnly = true)
    public List<RecurringResponse> list() {
        TenantContext.require();
        return templates.findAllByOrderByStatusAscNextRunDateAsc().stream().map(RecurringResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RecurringResponse get(UUID id) {
        TenantContext.require();
        return RecurringResponse.from(templates.findWithLinesById(id).orElseThrow(RecurringService::notFound));
    }

    @Transactional
    public RecurringResponse create(RecurringRequest r) {
        TenantContext.require();
        Organization org = organizations.currentEntity();
        Client client = activeClient(r.clientId());
        String currency = currencyFor(r.currency(), client, org);
        RecurringTemplate t = RecurringTemplate.create(client, r.name().trim(), currency, r.frequency(),
                r.intervalCount() == null ? 1 : r.intervalCount(), r.startDate());
        apply(t, r, client, currency, org);
        templates.save(t);
        audit.record("recurring", t.getId(), "created", "Recurring invoice \"" + t.getName() + "\" created",
                Map.of("frequency", t.getFrequency().name(), "next", String.valueOf(t.getNextRunDate())));
        return RecurringResponse.from(t);
    }

    @Transactional
    public RecurringResponse update(UUID id, RecurringRequest r) {
        TenantContext.require();
        RecurringTemplate t = lock(id);
        Organization org = organizations.currentEntity();
        Client client = activeClient(r.clientId());
        apply(t, r, client, currencyFor(r.currency(), client, org), org);
        audit.record("recurring", t.getId(), "updated", "Recurring invoice \"" + t.getName() + "\" updated", Map.of());
        return RecurringResponse.from(t);
    }

    @Transactional
    public RecurringResponse pause(UUID id) {
        TenantContext.require();
        RecurringTemplate t = lock(id);
        t.pause();
        audit.record("recurring", t.getId(), "paused", "Recurring invoice \"" + t.getName() + "\" paused", Map.of());
        return RecurringResponse.from(t);
    }

    @Transactional
    public RecurringResponse resume(UUID id) {
        TenantContext.require();
        RecurringTemplate t = lock(id);
        t.resume(organizations.today(organizations.currentEntity(), clock));
        audit.record("recurring", t.getId(), "resumed", "Recurring invoice \"" + t.getName() + "\" resumed", Map.of());
        return RecurringResponse.from(t);
    }

    @Transactional
    public RecurringResponse end(UUID id) {
        TenantContext.require();
        RecurringTemplate t = lock(id);
        t.end();
        audit.record("recurring", t.getId(), "ended", "Recurring invoice \"" + t.getName() + "\" ended", Map.of());
        return RecurringResponse.from(t);
    }

    @Transactional(readOnly = true)
    public List<RunResponse> runs(UUID id) {
        UUID orgId = TenantContext.require().organizationId();
        templates.findById(id).orElseThrow(RecurringService::notFound);
        return jdbc.query("""
                SELECT r.period_date, r.invoice_id, i.number, i.status, r.created_at
                FROM recurring_runs r LEFT JOIN invoices i ON i.id = r.invoice_id
                WHERE r.template_id = ? AND r.organization_id = ?
                ORDER BY r.period_date DESC
                """, (rs, n) -> new RunResponse(rs.getObject(1, LocalDate.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant()), id, orgId);
    }

    // ---- generation (called by the job, and by "Generate now") ----

    /**
     * Generates the invoice for the template's current period if it is due (or {@code force}).
     * The period is claimed with INSERT ... ON CONFLICT DO NOTHING on (template, period):
     * if it was already generated, nothing is created twice, the template just moves on.
     * Returns null when there was nothing to do.
     */
    @Transactional
    public Generated generateNext(UUID templateId, Instant now, boolean force) {
        CurrentTenant tenant = TenantContext.require();
        RecurringTemplate t = lock(templateId);
        Organization org = organizations.currentEntity();
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of(org.getTimeZone()));
        if (t.getStatus() != RecurringTemplate.Status.ACTIVE || t.getNextRunDate() == null) {
            if (force) {
                throw ApiException.conflict("Only active recurring invoices can generate invoices");
            }
            return null;
        }
        if (!force && !t.isDue(today)) {
            return null;
        }
        LocalDate period = t.getNextRunDate();
        UUID runId = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        int claimed = jdbc.update("""
                INSERT INTO recurring_runs (id, organization_id, template_id, period_date)
                VALUES (?, ?, ?, ?) ON CONFLICT (template_id, period_date) DO NOTHING
                """, runId, tenant.organizationId(), t.getId(), period);
        if (claimed == 0) {
            t.advance(now); // already generated (e.g. a retry after a crash): just move on
            return null;
        }

        Invoice invoice = invoices.createDraft(t.getClient(), t.getCurrency(), t.getNotes(), t.getTerms(), t.invoiceLines(),
                period, period.plusDays(t.getPaymentTermsDays()));
        jdbc.update("UPDATE recurring_runs SET invoice_id = ? WHERE id = ?", invoice.getId(), runId);
        boolean issued = false;
        if (t.isAutoIssue()) {
            invoices.issue(invoice.getId());
            issued = true;
        }
        t.advance(now);
        audit.record(tenant.organizationId(), tenant.userId(), "recurring", t.getId(), "generated",
                "Invoice generated for " + period + (issued ? " and issued" : " as a draft"),
                Map.of("invoiceId", invoice.getId().toString(), "period", period.toString()));
        return new Generated(invoice.getId(), issued, issued && t.isAutoEmail());
    }

    private void apply(RecurringTemplate t, RecurringRequest r, Client client, String currency, Organization org) {
        t.update(client, r.name().trim(), currency, r.frequency(), r.intervalCount() == null ? 1 : r.intervalCount(),
                r.startDate(), r.endDate(), r.paymentTermsDays() == null ? 30 : r.paymentTermsDays(), r.autoIssue(),
                r.autoEmail(), Strings.clean(r.notes()), Strings.clean(r.terms()), lineFactory.calculate(r.lines(), currency),
                organizations.today(org, clock));
    }

    private RecurringTemplate lock(UUID id) {
        RecurringTemplate t = templates.findForUpdate(id).orElseThrow(RecurringService::notFound);
        t.getLines().size();
        return t;
    }

    private Client activeClient(UUID clientId) {
        Client client = clients.find(clientId);
        if (client.isArchived()) {
            throw ApiException.badRequest("That client is archived. Restore it first.");
        }
        return client;
    }

    private static String currencyFor(String requested, Client client, Organization org) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return client.getCurrency() != null ? client.getCurrency() : org.getDefaultCurrency();
    }

    private static ApiException notFound() {
        return ApiException.notFound("Recurring invoice not found");
    }
}
