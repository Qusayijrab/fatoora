package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.document.DocumentNumberService;
import com.fatoora.invoicing.document.DocumentNumberService.DocType;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineDraftFactory;
import com.fatoora.invoicing.document.Party;
import com.fatoora.invoicing.invoice.InvoiceDtos.CreditNoteRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceResponse;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceSummary;
import com.fatoora.invoicing.invoice.InvoiceDtos.PaymentRequest;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.organization.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for invoices and credit notes. It loads, checks permissions,
 * takes numbers and writes audit lines; the rules themselves live in {@link Invoice}.
 */
@Service
public class InvoiceService {

    /** Default payment terms when no due date is given. */
    static final int DEFAULT_TERMS_DAYS = 30;

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ClientService clients;
    private final OrganizationService organizations;
    private final LineDraftFactory lineFactory;
    private final DocumentNumberService numbers;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, ClientService clients,
            OrganizationService organizations, LineDraftFactory lineFactory, DocumentNumberService numbers,
            AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.clients = clients;
        this.organizations = organizations;
        this.lineFactory = lineFactory;
        this.numbers = numbers;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Published after commit-worthy changes so other features (email, recurring) can react. */
    public record InvoiceIssued(UUID organizationId, UUID invoiceId) {}

    public record PaymentRecorded(UUID organizationId, UUID invoiceId, UUID paymentId) {}

    // ---- reads ----

    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(InvoiceKind kind, InvoiceStatus status, UUID clientId, String q, int page,
            int size) {
        TenantContext.require();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("createdAt")));
        return PageResponse.from(
                invoices.findAll(InvoiceRepository.matching(kind == null ? InvoiceKind.INVOICE : kind, status, clientId, q), pageable),
                InvoiceSummary::from);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        TenantContext.require();
        return toResponse(load(id));
    }

    // ---- drafts ----

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        TenantContext.require();
        Organization org = organizations.currentEntity();
        Client client = activeClient(request.clientId());
        String currency = currencyFor(request.currency(), client, org);
        LocalDate issueDate = request.issueDate() != null ? request.issueDate() : organizations.today(org, clock);
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : issueDate.plusDays(DEFAULT_TERMS_DAYS);

        Invoice invoice = Invoice.draft(client, currency, issueDate, dueDate);
        invoice.edit(client, currency, issueDate, dueDate, Strings.clean(request.notes()), Strings.clean(request.terms()),
                lineFactory.calculate(request.lines(), currency));
        invoices.save(invoice);
        audit.record("invoice", invoice.getId(), "created", "Draft invoice created for " + client.getName(),
                Map.of("total", invoice.getTotal().toString()));
        return toResponse(invoice);
    }

    /** Used by quote conversion: the quote's lines become an invoice draft. */
    @Transactional
    public Invoice createDraftFromLines(Client client, String currency, String notes, String terms,
            List<CalculatedLine> lines, UUID quoteId) {
        Organization org = organizations.currentEntity();
        LocalDate issueDate = organizations.today(org, clock);
        Invoice invoice = Invoice.draft(client, currency, issueDate, issueDate.plusDays(DEFAULT_TERMS_DAYS));
        invoice.edit(client, currency, issueDate, issueDate.plusDays(DEFAULT_TERMS_DAYS), notes, terms, lines);
        invoice.linkQuote(quoteId);
        invoices.save(invoice);
        audit.record("invoice", invoice.getId(), "created", "Draft invoice created from a quote",
                Map.of("quoteId", quoteId.toString(), "total", invoice.getTotal().toString()));
        return invoice;
    }

    @Transactional
    public InvoiceResponse update(UUID id, InvoiceRequest request) {
        TenantContext.require();
        Invoice invoice = loadForUpdate(id);
        Organization org = organizations.currentEntity();
        Client client = activeClient(request.clientId());
        String currency = currencyFor(request.currency(), client, org);
        LocalDate issueDate = request.issueDate() != null ? request.issueDate() : invoice.getIssueDate();
        LocalDate dueDate = request.dueDate() != null ? request.dueDate() : issueDate.plusDays(DEFAULT_TERMS_DAYS);
        invoice.edit(client, currency, issueDate, dueDate, Strings.clean(request.notes()), Strings.clean(request.terms()),
                lineFactory.calculate(request.lines(), currency));
        audit.record("invoice", invoice.getId(), "updated", "Draft invoice updated",
                Map.of("total", invoice.getTotal().toString()));
        return toResponse(invoice);
    }

    /** Drafts have no number yet, so deleting one leaves no gap in the sequence. */
    @Transactional
    public void deleteDraft(UUID id) {
        TenantContext.require();
        Invoice invoice = loadForUpdate(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw ApiException.conflict("Only drafts can be deleted. Void an issued invoice instead.");
        }
        audit.record("invoice", invoice.getId(), "deleted", "Draft invoice deleted", Map.of());
        invoices.delete(invoice);
    }

    // ---- lifecycle ----

    @Transactional
    public InvoiceResponse issue(UUID id) {
        CurrentTenant tenant = TenantContext.require();
        Invoice invoice = loadForUpdate(id);
        invoice.requireIssuable();
        Organization org = organizations.currentEntity();
        String number = numbers.next(tenant.organizationId(), DocType.INVOICE, invoice.getIssueDate().getYear());
        invoice.issue(number, Party.of(org), clock.instant());
        audit.record("invoice", invoice.getId(), "issued", "Invoice " + number + " issued",
                Map.of("number", number, "total", invoice.getTotal().toString()));
        events.publishEvent(new InvoiceIssued(tenant.organizationId(), invoice.getId()));
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse recordPayment(UUID id, PaymentRequest request) {
        CurrentTenant tenant = TenantContext.require();
        Invoice invoice = loadForUpdate(id);
        Organization org = organizations.currentEntity();
        Money amount = Money.parse(request.amount(), invoice.getCurrency());
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : organizations.today(org, clock);
        Payment payment = applyPayment(invoice, amount, request.method(), paidOn, Strings.clean(request.reference()),
                tenant.userId(), null, null);
        events.publishEvent(new PaymentRecorded(tenant.organizationId(), invoice.getId(), payment.getId()));
        return toResponse(invoice);
    }

    /**
     * Shared by manual payments and online payments (Phase 5). The caller must have
     * loaded the invoice with {@link #loadForUpdate}, so the balance it checks is current.
     */
    @Transactional
    public Payment applyPayment(Invoice invoice, Money amount, Payment.Method method, LocalDate paidOn, String reference,
            UUID recordedBy, String provider, String providerPaymentId) {
        invoice.recordPayment(amount);
        Payment payment = payments.save(new Payment(invoice, amount, method, paidOn, reference, recordedBy, provider,
                providerPaymentId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("amount", amount.toString());
        data.put("method", method.name());
        data.put("status", invoice.getStatus().name());
        if (provider != null) {
            data.put("provider", provider);
        }
        if (recordedBy == null) {
            audit.record(invoice.getOrganizationId(), null, "invoice", invoice.getId(), "payment_recorded",
                    "Payment of " + amount + " received online for " + invoice.getNumber(), data);
        } else {
            audit.record(invoice.getOrganizationId(), recordedBy, "invoice", invoice.getId(), "payment_recorded",
                    "Payment of " + amount + " recorded for " + invoice.getNumber(), data);
        }
        return payment;
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID id, String reason) {
        TenantContext.require().requireRole(Role.ADMIN);
        Invoice invoice = loadForUpdate(id);
        invoice.voidInvoice(reason.trim(), clock.instant());
        audit.record("invoice", invoice.getId(), "voided",
                "Invoice " + (invoice.getNumber() == null ? "draft" : invoice.getNumber()) + " voided",
                Map.of("reason", reason.trim()));
        return toResponse(invoice);
    }

    /** Issues a credit note against an open invoice and applies it to that invoice's balance. */
    @Transactional
    public InvoiceResponse createCreditNote(UUID invoiceId, CreditNoteRequest request) {
        CurrentTenant tenant = TenantContext.require().requireRole(Role.ADMIN);
        Invoice original = loadForUpdate(invoiceId);
        Organization org = organizations.currentEntity();
        LocalDate today = organizations.today(org, clock);

        Invoice creditNote = Invoice.creditNoteFor(original, today, request.reason().trim());
        creditNote.edit(original.getClient(), original.getCurrency(), today, null, request.reason().trim(), null,
                lineFactory.calculate(request.lines(), original.getCurrency()));
        creditNote.requireIssuable();
        String number = numbers.next(tenant.organizationId(), DocType.CREDIT_NOTE, today.getYear());
        creditNote.issue(number, Party.of(org), clock.instant());
        original.applyCredit(creditNote.getTotal());
        invoices.save(creditNote);

        audit.record("invoice", creditNote.getId(), "issued",
                "Credit note " + number + " issued against " + original.getNumber(),
                Map.of("number", number, "total", creditNote.getTotal().toString(), "invoice", original.getNumber()));
        audit.record("invoice", original.getId(), "credited",
                "Credited " + creditNote.getTotal() + " by " + number,
                Map.of("creditNote", number, "status", original.getStatus().name()));
        return toResponse(original);
    }

    // ---- helpers ----

    /** Loads and row-locks for a change. Lines are fetched lazily afterwards. */
    public Invoice loadForUpdate(UUID id) {
        return invoices.findForUpdate(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    /** Loads with lines; a foreign or unknown id is "not found" (the tenant filter hides it). */
    public Invoice load(UUID id) {
        return invoices.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
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

    private InvoiceResponse toResponse(Invoice invoice) {
        List<Payment> paid = invoice.getId() == null ? List.of()
                : payments.findByInvoice_IdOrderByPaidOnAscCreatedAtAsc(invoice.getId());
        List<Invoice> credits = invoice.getKind() == InvoiceKind.INVOICE && invoice.getId() != null
                ? invoices.findByCreditedInvoice_IdOrderByIssueDateAscCreatedAtAsc(invoice.getId())
                : List.of();
        return InvoiceResponse.from(invoice, paid, credits);
    }
}
