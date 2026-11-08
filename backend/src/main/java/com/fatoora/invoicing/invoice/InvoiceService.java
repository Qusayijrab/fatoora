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
import com.fatoora.invoicing.currency.FxService;
import com.fatoora.invoicing.document.DocumentNumberService;
import com.fatoora.invoicing.document.DocumentNumberService.DocType;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineDraftFactory;
import com.fatoora.invoicing.document.Party;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.document.LineCalculator;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import com.fatoora.invoicing.invoice.InvoiceDtos.CreditLineInput;
import com.fatoora.invoicing.invoice.InvoiceDtos.CreditNoteRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.CreditableLine;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceRequest;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceResponse;
import com.fatoora.invoicing.invoice.InvoiceDtos.InvoiceSummary;
import com.fatoora.invoicing.invoice.InvoiceDtos.PaymentRequest;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.organization.Role;
import java.math.BigDecimal;
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
    private final FxService fx;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, PaymentRepository payments, ClientService clients,
            OrganizationService organizations, LineDraftFactory lineFactory, DocumentNumberService numbers,
            AuditService audit, ApplicationEventPublisher events, FxService fx, Clock clock) {
        this.invoices = invoices;
        this.payments = payments;
        this.clients = clients;
        this.organizations = organizations;
        this.lineFactory = lineFactory;
        this.numbers = numbers;
        this.audit = audit;
        this.events = events;
        this.fx = fx;
        this.clock = clock;
    }

    /** Published after commit-worthy changes so other features (email, recurring) can react. */
    public record InvoiceIssued(UUID organizationId, UUID invoiceId) {}

    public record PaymentRecorded(UUID organizationId, UUID invoiceId, UUID paymentId) {}

    /**
     * The balance of an issued invoice changed (payment, credit note, refund, void).
     * Delivered after commit; online checkouts for an old balance are expired on it.
     */
    public record InvoiceBalanceChanged(UUID organizationId, UUID invoiceId, Money balanceDue, boolean payable) {

        static InvoiceBalanceChanged of(Invoice i) {
            return new InvoiceBalanceChanged(i.getOrganizationId(), i.getId(), i.balanceDue(),
                    i.getStatus().isOpen() && i.balanceDue().isPositive());
        }
    }

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

    /** Used by quote conversion: the quote's lines become an invoice draft dated today. */
    @Transactional
    public Invoice createDraftFromLines(Client client, String currency, String notes, String terms,
            List<CalculatedLine> lines, UUID quoteId) {
        LocalDate issueDate = organizations.today(organizations.currentEntity(), clock);
        Invoice invoice = createDraft(client, currency, notes, terms, lines, issueDate, issueDate.plusDays(DEFAULT_TERMS_DAYS));
        invoice.linkQuote(quoteId);
        audit.record("invoice", invoice.getId(), "created", "Draft invoice created from a quote",
                Map.of("quoteId", quoteId.toString(), "total", invoice.getTotal().toString()));
        return invoice;
    }

    /** Used by recurring invoices: a draft for a given period date. */
    @Transactional
    public Invoice createDraft(Client client, String currency, String notes, String terms, List<CalculatedLine> lines,
            LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = Invoice.draft(client, currency, issueDate, dueDate);
        invoice.edit(client, currency, issueDate, dueDate, notes, terms, lines);
        // Flushed now: callers reference the new row from plain SQL in the same transaction.
        return invoices.saveAndFlush(invoice);
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

    /** Issues dated today (due date moved by the same terms), or with the draft's own dates if {@code keepDates}. */
    @Transactional
    public InvoiceResponse issue(UUID id) {
        return issue(id, false);
    }

    @Transactional
    public InvoiceResponse issue(UUID id, boolean keepDates) {
        CurrentTenant tenant = TenantContext.require();
        Organization org = organizations.currentEntity();
        LocalDate today = organizations.today(org, clock);
        // Look the rate up before locking the row: an external rate source (ECB) is then
        // called without holding the lock, and the locked lookup below hits its cache.
        invoices.findCurrencyAndDate(id).ifPresent(draft -> fx.find(draft.getCurrency(), org.getDefaultCurrency(),
                keepDates ? draft.getIssueDate() : today));
        Invoice invoice = loadForUpdate(id);
        invoice.requireIssuable();
        invoice.dateForIssue(today, keepDates);
        // Rate first: if there is none, nothing (not even a number) is used up.
        FxService.Rate rate = fx.rateFor(invoice.getCurrency(), org.getDefaultCurrency(), invoice.getIssueDate());
        String number = numbers.next(tenant.organizationId(), DocType.INVOICE, invoice.getIssueDate().getYear());
        invoice.issue(number, Party.of(org), clock.instant(), org.getDefaultCurrency(), rate.value(), rate.source());
        audit.record("invoice", invoice.getId(), "issued", "Invoice " + number + " issued",
                Map.of("number", number, "total", invoice.getTotal().toString(),
                        "rate", rate.value().toPlainString() + " (" + rate.source() + ")"));
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
                    "Payment of " + amount.toDisplayString() + " received online for " + invoice.getNumber(), data);
        } else {
            audit.record(invoice.getOrganizationId(), recordedBy, "invoice", invoice.getId(), "payment_recorded",
                    "Payment of " + amount.toDisplayString() + " recorded for " + invoice.getNumber(), data);
        }
        events.publishEvent(InvoiceBalanceChanged.of(invoice));
        return payment;
    }

    /** Records money given back to the client; only what credit notes left owed (see {@link Invoice#refundDue()}). */
    @Transactional
    public InvoiceResponse recordRefund(UUID id, PaymentRequest request) {
        CurrentTenant tenant = TenantContext.require().requireRole(Role.ADMIN);
        Invoice invoice = loadForUpdate(id);
        Organization org = organizations.currentEntity();
        Money amount = Money.parse(request.amount(), invoice.getCurrency());
        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : organizations.today(org, clock);
        invoice.recordRefund(amount);
        payments.save(Payment.refund(invoice, amount, request.method(), paidOn, Strings.clean(request.reference()),
                tenant.userId()));
        audit.record("invoice", invoice.getId(), "refund_recorded",
                "Refund of " + amount.toDisplayString() + " recorded for " + invoice.getNumber(),
                Map.of("amount", amount.toString(), "method", request.method().name()));
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(UUID id, String reason) {
        TenantContext.require().requireRole(Role.ADMIN);
        Invoice invoice = loadForUpdate(id);
        invoice.voidInvoice(reason.trim(), clock.instant());
        audit.record("invoice", invoice.getId(), "voided",
                "Invoice " + (invoice.getNumber() == null ? "draft" : invoice.getNumber()) + " voided",
                Map.of("reason", reason.trim()));
        events.publishEvent(InvoiceBalanceChanged.of(invoice));
        return toResponse(invoice);
    }

    /**
     * Issues a credit note against an issued invoice and applies it. On an invoice the
     * client already paid (fully or partly), the part beyond the balance becomes a refund due.
     */
    @Transactional
    public InvoiceResponse createCreditNote(UUID invoiceId, CreditNoteRequest request) {
        CurrentTenant tenant = TenantContext.require().requireRole(Role.ADMIN);
        Invoice original = loadForUpdate(invoiceId);
        Organization org = organizations.currentEntity();
        LocalDate today = organizations.today(org, clock);

        Invoice creditNote = Invoice.creditNoteFor(original, today, request.reason().trim());
        List<UUID> creditedLineIds = request.lines().stream().map(CreditLineInput::invoiceLineId).toList();
        creditNote.edit(original.getClient(), original.getCurrency(), today, null, request.reason().trim(), null,
                creditLines(original, request.lines()));
        creditNote.linkCreditedLines(creditedLineIds);
        creditNote.requireIssuable();
        // A credit note reuses the original invoice's frozen rate, so it offsets it exactly.
        String number = numbers.next(tenant.organizationId(), DocType.CREDIT_NOTE, today.getYear());
        creditNote.issue(number, Party.of(org), clock.instant(), original.getBaseCurrency(), original.getExchangeRate(),
                original.getRateSource());
        original.applyCredit(creditNote.getTotal());
        invoices.save(creditNote);

        audit.record("invoice", creditNote.getId(), "issued",
                "Credit note " + number + " issued against " + original.getNumber(),
                Map.of("number", number, "total", creditNote.getTotal().toString(), "invoice", original.getNumber()));
        audit.record("invoice", original.getId(), "credited",
                "Credited " + creditNote.getTotal().toDisplayString() + " by " + number,
                Map.of("creditNote", number, "status", original.getStatus().name()));
        events.publishEvent(InvoiceBalanceChanged.of(original));
        return toResponse(original);
    }

    /**
     * Builds credit note lines from the original invoice's lines: same description, unit
     * and tax rate; the quantity asked for; the original unit price or a lower one. Across
     * all credit notes, an original line can never be credited beyond its quantity or its
     * total, so a credit note only ever returns what was actually invoiced.
     */
    private List<CalculatedLine> creditLines(Invoice original, List<CreditLineInput> inputs) {
        String currency = original.getCurrency();
        Map<UUID, InvoiceLine> originals = new LinkedHashMap<>();
        original.getLines().forEach(l -> originals.put(l.getId(), l));
        Map<UUID, BigDecimal> qtyUsed = new LinkedHashMap<>();
        Map<UUID, Long> totalUsed = new LinkedHashMap<>();
        for (var so : invoices.creditedLines(original.getId())) {
            qtyUsed.put(so.getLineId(), so.getQuantity());
            totalUsed.put(so.getLineId(), so.getTotalMinor());
        }

        List<CalculatedLine> lines = new java.util.ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            CreditLineInput in = inputs.get(i);
            String field = "lines[" + i + "]";
            InvoiceLine line = originals.get(in.invoiceLineId());
            if (line == null) {
                throw ApiException.badRequest(field + ": that line is not on invoice " + original.getNumber());
            }
            BigDecimal quantity = new BigDecimal(in.quantity());
            if (quantity.signum() <= 0) {
                throw ApiException.badRequest(field + ": the quantity must be more than zero");
            }
            BigDecimal qtyLeft = line.getQuantity().subtract(qtyUsed.getOrDefault(line.getId(), BigDecimal.ZERO));
            if (quantity.compareTo(qtyLeft) > 0) {
                throw ApiException.badRequest(field + ": only " + qtyLeft.stripTrailingZeros().toPlainString()
                        + " of \"" + line.getDescription() + "\" is left to credit");
            }
            Money originalPrice = line.unitPrice(currency);
            Money price = in.unitPrice() == null ? originalPrice : Money.parse(in.unitPrice(), currency);
            if (!price.isPositive() || price.compareTo(originalPrice) > 0) {
                throw ApiException.badRequest(field + ": the unit price must be more than zero and at most the invoiced "
                        + originalPrice);
            }
            CalculatedLine credit = LineCalculator.calculate(new LineDraft(line.getProductId(), line.getDescription(),
                    quantity, line.getUnit(), price, line.getTaxRateId(), line.getTaxRate()));
            long totalLeft = line.total(currency).amountMinor() - totalUsed.getOrDefault(line.getId(), 0L);
            if (credit.total().amountMinor() > totalLeft) {
                throw ApiException.badRequest(field + ": only " + Money.ofMinor(totalLeft, currency)
                        + " of \"" + line.getDescription() + "\" is left to credit");
            }
            qtyUsed.merge(line.getId(), quantity, BigDecimal::add);
            totalUsed.merge(line.getId(), credit.total().amountMinor(), Long::sum);
            lines.add(credit);
        }
        return lines;
    }

    /** Per invoice line, what credit notes may still credit (for the credit note dialog). */
    private List<CreditableLine> creditable(Invoice invoice) {
        String currency = invoice.getCurrency();
        Map<UUID, InvoiceRepository.CreditedSoFar> used = new LinkedHashMap<>();
        invoices.creditedLines(invoice.getId()).forEach(c -> used.put(c.getLineId(), c));
        return invoice.getLines().stream().map(l -> {
            var u = used.get(l.getId());
            BigDecimal qty = l.getQuantity().subtract(u == null ? BigDecimal.ZERO : u.getQuantity());
            long total = l.total(currency).amountMinor() - (u == null ? 0L : u.getTotalMinor());
            return new CreditableLine(l.getId(), qty.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString(),
                    MoneyDto.from(Money.ofMinor(Math.max(0, total), currency)));
        }).toList();
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
        List<CreditableLine> creditable = invoice.getKind() == InvoiceKind.INVOICE && invoice.getId() != null
                && invoice.getStatus() != InvoiceStatus.DRAFT ? creditable(invoice) : List.of();
        return InvoiceResponse.from(invoice, paid, credits, creditable);
    }
}
