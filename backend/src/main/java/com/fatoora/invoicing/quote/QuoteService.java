package com.fatoora.invoicing.quote;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.document.DocumentNumberService;
import com.fatoora.invoicing.document.DocumentNumberService.DocType;
import com.fatoora.invoicing.document.LineDraftFactory;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceService;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteRequest;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteResponse;
import com.fatoora.invoicing.quote.QuoteDtos.QuoteSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {

    /** Default validity when none is given. */
    static final int DEFAULT_VALID_DAYS = 30;

    private final QuoteRepository quotes;
    private final ClientService clients;
    private final OrganizationService organizations;
    private final LineDraftFactory lineFactory;
    private final DocumentNumberService numbers;
    private final InvoiceService invoices;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public QuoteService(QuoteRepository quotes, ClientService clients, OrganizationService organizations,
            LineDraftFactory lineFactory, DocumentNumberService numbers, InvoiceService invoices, AuditService audit,
            ApplicationEventPublisher events, Clock clock) {
        this.quotes = quotes;
        this.clients = clients;
        this.organizations = organizations;
        this.lineFactory = lineFactory;
        this.numbers = numbers;
        this.invoices = invoices;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record QuoteSent(UUID organizationId, UUID quoteId) {}

    @Transactional(readOnly = true)
    public PageResponse<QuoteSummary> list(QuoteStatus status, UUID clientId, String q, int page, int size) {
        TenantContext.require();
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("createdAt")));
        return PageResponse.from(quotes.findAll(QuoteRepository.matching(status, clientId, q), pageable),
                QuoteSummary::from);
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(UUID id) {
        TenantContext.require();
        return QuoteResponse.from(quotes.findWithLinesById(id).orElseThrow(QuoteService::notFound));
    }

    @Transactional
    public QuoteResponse create(QuoteRequest r) {
        TenantContext.require();
        Organization org = organizations.currentEntity();
        Client client = activeClient(r.clientId());
        String currency = currencyFor(r.currency(), client, org);
        LocalDate issueDate = r.issueDate() != null ? r.issueDate() : organizations.today(org, clock);
        LocalDate validUntil = r.validUntil() != null ? r.validUntil() : issueDate.plusDays(DEFAULT_VALID_DAYS);
        Quote quote = Quote.draft(client, currency, issueDate);
        quote.edit(client, currency, issueDate, validUntil, Strings.clean(r.notes()), Strings.clean(r.terms()),
                lineFactory.calculate(r.lines(), currency));
        quotes.save(quote);
        audit.record("quote", quote.getId(), "created", "Draft quote created for " + client.getName(),
                Map.of("total", quote.getTotal().toString()));
        return QuoteResponse.from(quote);
    }

    @Transactional
    public QuoteResponse update(UUID id, QuoteRequest r) {
        TenantContext.require();
        Quote quote = lock(id);
        Organization org = organizations.currentEntity();
        Client client = activeClient(r.clientId());
        String currency = currencyFor(r.currency(), client, org);
        LocalDate issueDate = r.issueDate() != null ? r.issueDate() : quote.getIssueDate();
        LocalDate validUntil = r.validUntil() != null ? r.validUntil() : issueDate.plusDays(DEFAULT_VALID_DAYS);
        quote.edit(client, currency, issueDate, validUntil, Strings.clean(r.notes()), Strings.clean(r.terms()),
                lineFactory.calculate(r.lines(), currency));
        audit.record("quote", quote.getId(), "updated", "Draft quote updated", Map.of("total", quote.getTotal().toString()));
        return QuoteResponse.from(quote);
    }

    @Transactional
    public void deleteDraft(UUID id) {
        TenantContext.require();
        Quote quote = lock(id);
        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw ApiException.conflict("Only draft quotes can be deleted");
        }
        audit.record("quote", quote.getId(), "deleted", "Draft quote deleted", Map.of());
        quotes.delete(quote);
    }

    /** Numbers the quote (QUO-2026-0001) and freezes the client's details. Email goes out in Phase 4. */
    @Transactional
    public QuoteResponse send(UUID id) {
        CurrentTenant tenant = TenantContext.require();
        Quote quote = lock(id);
        quote.requireSendable();
        String number = numbers.next(tenant.organizationId(), DocType.QUOTE, quote.getIssueDate().getYear());
        quote.send(number, clock.instant());
        audit.record("quote", quote.getId(), "sent", "Quote " + number + " sent",
                Map.of("number", number, "total", quote.getTotal().toString()));
        events.publishEvent(new QuoteSent(tenant.organizationId(), quote.getId()));
        return QuoteResponse.from(quote);
    }

    @Transactional
    public QuoteResponse accept(UUID id) {
        TenantContext.require();
        Quote quote = lock(id);
        quote.accept(clock.instant());
        audit.record("quote", quote.getId(), "accepted", "Quote " + quote.getNumber() + " accepted", Map.of());
        return QuoteResponse.from(quote);
    }

    @Transactional
    public QuoteResponse reject(UUID id) {
        TenantContext.require();
        Quote quote = lock(id);
        quote.reject(clock.instant());
        audit.record("quote", quote.getId(), "rejected", "Quote " + quote.getNumber() + " rejected", Map.of());
        return QuoteResponse.from(quote);
    }

    /** Copies an accepted quote into a new invoice draft. Returns the invoice id. */
    @Transactional
    public UUID convertToInvoice(UUID id) {
        TenantContext.require();
        Quote quote = lock(id);
        Invoice invoice = invoices.createDraftFromLines(quote.getClient(), quote.getCurrency(), quote.getNotes(),
                quote.getTerms(), quote.linesForConversion(), quote.getId());
        quote.markConverted(invoice.getId());
        audit.record("quote", quote.getId(), "converted", "Quote " + quote.getNumber() + " turned into an invoice draft",
                Map.of("invoiceId", invoice.getId().toString()));
        return invoice.getId();
    }

    private Quote lock(UUID id) {
        Quote quote = quotes.findForUpdate(id).orElseThrow(QuoteService::notFound);
        quote.getLines().size(); // load lines inside the transaction
        return quote;
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
        return ApiException.notFound("Quote not found");
    }
}
