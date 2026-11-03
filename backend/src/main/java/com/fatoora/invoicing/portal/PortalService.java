package com.fatoora.invoicing.portal;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientService;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.common.tenancy.TenantRunner;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.invoice.InvoiceStatus;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.pdf.DocumentPdfService;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import com.fatoora.invoicing.publiclink.PublicLink;
import com.fatoora.invoicing.publiclink.PublicLinkService;
import com.fatoora.invoicing.quote.Quote;
import com.fatoora.invoicing.quote.QuoteRepository;
import com.fatoora.invoicing.quote.QuoteStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The client portal (CLAUDE.md rule 9): one unguessable link per client. Whoever holds it
 * sees that client's issued invoices and sent quotes in that organization, and nothing
 * else; can download PDFs, pay open invoices, and accept or reject quotes.
 */
@Service
public class PortalService {

    public record PortalInvoice(UUID id, InvoiceKind kind, String number, InvoiceStatus status, LocalDate issueDate,
            LocalDate dueDate, MoneyDto total, MoneyDto balanceDue, String payUrl) {}

    public record PortalQuote(UUID id, String number, QuoteStatus status, LocalDate issueDate, LocalDate validUntil,
            MoneyDto total) {}

    public record PortalView(String organizationName, String organizationEmail, String clientName,
            List<MoneyDto> outstanding, List<PortalInvoice> invoices, List<PortalQuote> quotes) {}

    private final PublicLinkService links;
    private final ClientService clients;
    private final InvoiceRepository invoices;
    private final QuoteRepository quotes;
    private final OrganizationService organizations;
    private final DocumentPdfService pdfs;
    private final AuditService audit;
    private final TenantRunner runner;
    private final Clock clock;

    public PortalService(PublicLinkService links, ClientService clients, InvoiceRepository invoices,
            QuoteRepository quotes, OrganizationService organizations, DocumentPdfService pdfs, AuditService audit,
            TenantRunner runner, Clock clock) {
        this.links = links;
        this.clients = clients;
        this.invoices = invoices;
        this.quotes = quotes;
        this.organizations = organizations;
        this.pdfs = pdfs;
        this.audit = audit;
        this.runner = runner;
        this.clock = clock;
    }

    // ---- for logged-in users ----

    @Transactional
    public String portalLink(UUID clientId) {
        TenantContext.require();
        Client client = clients.find(clientId);
        return links.portalUrl(links.getOrCreate(PublicLink.Kind.CLIENT_PORTAL, client.getId()));
    }

    /** Revokes the current link; the next request creates a fresh one with a new token. */
    @Transactional
    public void revokePortalLink(UUID clientId) {
        TenantContext.require();
        Client client = clients.find(clientId);
        links.revoke(PublicLink.Kind.CLIENT_PORTAL, client.getId());
        audit.record("client", client.getId(), "portal_revoked", "Portal link for " + client.getName() + " revoked", Map.of());
    }

    // ---- public ----

    public PortalView view(String token) {
        PublicLink link = links.resolve(token, PublicLink.Kind.CLIENT_PORTAL);
        return runner.inOrganization(link.getOrganizationId(), () -> {
            Client client = clients.find(link.getTargetId());
            var org = organizations.currentEntity();
            Sort newest = Sort.by(Sort.Order.desc("issueDate"), Sort.Order.desc("createdAt"));

            List<PortalInvoice> invoiceViews = new ArrayList<>();
            Map<String, Money> owed = new TreeMap<>();
            for (Invoice i : invoices.findAll(clientDocuments(client.getId()), newest)) {
                String payUrl = null;
                if (i.getKind() == InvoiceKind.INVOICE && i.getStatus().isOpen()) {
                    payUrl = links.payUrl(links.getOrCreate(PublicLink.Kind.INVOICE, i.getId()));
                    owed.merge(i.getCurrency(), i.balanceDue(), Money::plus);
                }
                invoiceViews.add(new PortalInvoice(i.getId(), i.getKind(), i.getNumber(), i.getStatus(), i.getIssueDate(),
                        i.getDueDate(), MoneyDto.from(i.getTotal()), MoneyDto.from(i.balanceDue()), payUrl));
            }
            List<PortalQuote> quoteViews = quotes.findAll(QuoteRepository.matching(null, client.getId(), null), newest).stream()
                    .filter(q -> q.getStatus() != QuoteStatus.DRAFT)
                    .map(q -> new PortalQuote(q.getId(), q.getNumber(), q.getStatus(), q.getIssueDate(), q.getValidUntil(),
                            MoneyDto.from(q.getTotal())))
                    .toList();
            return new PortalView(org.getName(), org.getEmail(), client.getName(),
                    owed.values().stream().map(MoneyDto::from).toList(), invoiceViews, quoteViews);
        });
    }

    public PdfFile invoicePdf(String token, UUID invoiceId) {
        PublicLink link = links.resolve(token, PublicLink.Kind.CLIENT_PORTAL);
        return runner.inOrganization(link.getOrganizationId(), () -> {
            Invoice invoice = invoices.findWithLinesById(invoiceId)
                    .filter(i -> i.getClient().getId().equals(link.getTargetId()) && i.getStatus() != InvoiceStatus.DRAFT)
                    .orElseThrow(() -> ApiException.notFound("Not found"));
            return pdfs.invoicePdf(invoice, organizations.currentEntity());
        });
    }

    public PdfFile quotePdf(String token, UUID quoteId) {
        PublicLink link = links.resolve(token, PublicLink.Kind.CLIENT_PORTAL);
        return runner.inOrganization(link.getOrganizationId(), () -> pdfs.quotePdf(ownQuote(link, quoteId, false),
                organizations.currentEntity()));
    }

    /** The client answers a sent quote from the portal. */
    public QuoteStatus answerQuote(String token, UUID quoteId, boolean accept) {
        PublicLink link = links.resolve(token, PublicLink.Kind.CLIENT_PORTAL);
        return runner.inOrganization(link.getOrganizationId(), () -> {
            Quote quote = ownQuote(link, quoteId, true);
            var now = clock.instant();
            if (accept) {
                quote.accept(now);
            } else {
                quote.reject(now);
            }
            audit.record(link.getOrganizationId(), null, "quote", quote.getId(), accept ? "accepted" : "rejected",
                    "Quote " + quote.getNumber() + (accept ? " accepted" : " rejected") + " by the client in the portal",
                    Map.of("via", "portal"));
            return quote.getStatus();
        });
    }

    private Quote ownQuote(PublicLink link, UUID quoteId, boolean lock) {
        var found = lock ? quotes.findForUpdate(quoteId) : quotes.findWithLinesById(quoteId);
        return found
                .filter(q -> q.getClient().getId().equals(link.getTargetId()) && q.getStatus() != QuoteStatus.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Not found"));
    }

    private static org.springframework.data.jpa.domain.Specification<Invoice> clientDocuments(UUID clientId) {
        return (root, cq, cb) -> cb.and(
                cb.equal(root.get("client").get("id"), clientId),
                cb.notEqual(root.get("status"), InvoiceStatus.DRAFT));
    }
}
