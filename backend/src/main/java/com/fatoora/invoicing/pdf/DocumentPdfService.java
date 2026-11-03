package com.fatoora.invoicing.pdf;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.invoice.InvoiceStatus;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.quote.Quote;
import com.fatoora.invoicing.quote.QuoteRepository;
import com.fatoora.invoicing.quote.QuoteStatus;
import com.fatoora.invoicing.storage.DocumentStorage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PDFs for invoices, credit notes and quotes.
 *
 * <p>Issued documents never change their content, only their state (paid, void...), so
 * each rendered state is stored once in object storage under a key that includes that
 * state, and served from there afterwards. Drafts are rendered on the fly, with a DRAFT
 * watermark, and never stored.
 */
@Service
public class DocumentPdfService {

    public record PdfFile(String filename, byte[] content) {}

    private final InvoiceRepository invoices;
    private final QuoteRepository quotes;
    private final OrganizationService organizations;
    private final PdfRenderer renderer;
    private final DocumentStorage storage;

    public DocumentPdfService(InvoiceRepository invoices, QuoteRepository quotes, OrganizationService organizations,
            PdfRenderer renderer, DocumentStorage storage) {
        this.invoices = invoices;
        this.quotes = quotes;
        this.organizations = organizations;
        this.renderer = renderer;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PdfFile invoicePdf(UUID id) {
        TenantContext.require();
        Invoice invoice = invoices.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        return invoicePdf(invoice, organizations.currentEntity());
    }

    /** For callers that already hold the invoice (email, client portal). */
    public PdfFile invoicePdf(Invoice invoice, Organization org) {
        String prefix = invoice.getKind() == InvoiceKind.CREDIT_NOTE ? "credit-note" : "invoice";
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            return new PdfFile(prefix + "-draft.pdf", renderer.render("document", DocumentViews.of(invoice, org)));
        }
        long settled = invoice.getAmountPaid().amountMinor() + invoice.getAmountCredited().amountMinor();
        String key = "orgs/%s/invoices/%s/%s-%s-%d.pdf".formatted(
                invoice.getOrganizationId(), invoice.getId(), invoice.getNumber(), invoice.getStatus(), settled);
        byte[] pdf = storage.get(key).orElseGet(() -> {
            byte[] rendered = renderer.render("document", DocumentViews.of(invoice, org));
            storage.put(key, rendered, "application/pdf");
            return rendered;
        });
        return new PdfFile(invoice.getNumber() + ".pdf", pdf);
    }

    @Transactional(readOnly = true)
    public PdfFile quotePdf(UUID id) {
        TenantContext.require();
        Quote quote = quotes.findWithLinesById(id).orElseThrow(() -> ApiException.notFound("Quote not found"));
        return quotePdf(quote, organizations.currentEntity());
    }

    public PdfFile quotePdf(Quote quote, Organization org) {
        if (quote.getStatus() == QuoteStatus.DRAFT) {
            return new PdfFile("quote-draft.pdf", renderer.render("document", DocumentViews.of(quote, org)));
        }
        String key = "orgs/%s/quotes/%s/%s-%s.pdf".formatted(
                quote.getOrganizationId(), quote.getId(), quote.getNumber(), quote.getStatus());
        byte[] pdf = storage.get(key).orElseGet(() -> {
            byte[] rendered = renderer.render("document", DocumentViews.of(quote, org));
            storage.put(key, rendered, "application/pdf");
            return rendered;
        });
        return new PdfFile(quote.getNumber() + ".pdf", pdf);
    }
}
