package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.common.tenancy.TenantRunner;
import com.fatoora.invoicing.document.DocumentDtos.LineResponse;
import com.fatoora.invoicing.invoice.Invoice;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.invoice.InvoiceService;
import com.fatoora.invoicing.invoice.InvoiceStatus;
import com.fatoora.invoicing.mail.DocumentEmailService.PayLinkProvider;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.OrganizationService;
import com.fatoora.invoicing.pdf.DocumentPdfService;
import com.fatoora.invoicing.pdf.DocumentPdfService.PdfFile;
import com.fatoora.invoicing.publiclink.PublicLink;
import com.fatoora.invoicing.publiclink.PublicLinkService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment links: one stable, unguessable URL per invoice. Opening it shows the invoice;
 * "Pay" starts a fresh provider checkout for whatever is still owed at that moment.
 */
@Service
public class PaymentLinkService {

    public record PublicInvoiceView(
            String organizationName,
            String number,
            InvoiceStatus status,
            String clientName,
            String currency,
            LocalDate issueDate,
            LocalDate dueDate,
            List<LineResponse> lines,
            MoneyDto subtotal,
            MoneyDto tax,
            MoneyDto total,
            MoneyDto amountPaid,
            MoneyDto balanceDue,
            boolean payable,
            String provider) {}

    public record MockCheckoutView(String organizationName, String invoiceNumber, MoneyDto amount, String status, String payToken) {}

    private final PublicLinkService links;
    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;
    private final OrganizationService organizations;
    private final CheckoutSessionRepository sessions;
    private final PaymentGateway gateway;
    private final DocumentPdfService pdfs;
    private final TenantRunner runner;

    public PaymentLinkService(PublicLinkService links, InvoiceRepository invoices, InvoiceService invoiceService,
            OrganizationService organizations, CheckoutSessionRepository sessions, PaymentGateway gateway,
            DocumentPdfService pdfs, TenantRunner runner) {
        this.links = links;
        this.invoices = invoices;
        this.invoiceService = invoiceService;
        this.organizations = organizations;
        this.sessions = sessions;
        this.gateway = gateway;
        this.pdfs = pdfs;
        this.runner = runner;
    }

    // ---- for logged-in users ----

    /** The invoice's payment URL, created on first request. Only for issued, unpaid invoices. */
    @Transactional
    public String linkForInvoice(UUID invoiceId) {
        TenantContext.require();
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (invoice.getKind() != InvoiceKind.INVOICE || !invoice.getStatus().isOpen()) {
            throw ApiException.conflict("Payment links are for issued invoices with a balance due");
        }
        return payUrlFor(invoice);
    }

    /** Joins the caller's transaction (used while composing invoice emails). */
    @Transactional
    public String payUrlFor(Invoice invoice) {
        return links.payUrl(links.getOrCreate(PublicLink.Kind.INVOICE, invoice.getId()));
    }

    // ---- public: no login, the token is the permission ----

    public PublicInvoiceView publicView(String token) {
        PublicLink link = links.resolve(token, PublicLink.Kind.INVOICE);
        return runner.inOrganization(link.getOrganizationId(), () -> {
            Invoice i = invoiceService.load(link.getTargetId());
            Organization org = organizations.currentEntity();
            String cur = i.getCurrency();
            return new PublicInvoiceView(org.getName(), i.getNumber(), i.getStatus(), i.displayClientName(), cur,
                    i.getIssueDate(), i.getDueDate(), i.getLines().stream().map(l -> LineResponse.from(l, cur)).toList(),
                    MoneyDto.from(i.getSubtotal()), MoneyDto.from(i.getTax()), MoneyDto.from(i.getTotal()),
                    MoneyDto.from(i.getAmountPaid().plus(i.getAmountCredited())), MoneyDto.from(i.balanceDue()),
                    i.getStatus().isOpen() && i.balanceDue().isPositive(), gateway.name());
        });
    }

    public PdfFile publicPdf(String token) {
        PublicLink link = links.resolve(token, PublicLink.Kind.INVOICE);
        return runner.inOrganization(link.getOrganizationId(),
                () -> pdfs.invoicePdf(invoiceService.load(link.getTargetId()), organizations.currentEntity()));
    }

    /** Starts a checkout for the current balance and returns the provider's URL. */
    public String startCheckout(String token) {
        PublicLink link = links.resolve(token, PublicLink.Kind.INVOICE);
        return runner.inOrganization(link.getOrganizationId(), () -> {
            Invoice invoice = invoiceService.loadForUpdate(link.getTargetId());
            if (!invoice.getStatus().isOpen() || !invoice.balanceDue().isPositive()) {
                throw ApiException.conflict("This invoice has nothing left to pay");
            }
            String payUrl = links.payUrl(link);
            var checkout = gateway.createCheckout(new PaymentGateway.CheckoutRequest(
                    invoice.getOrganizationId(), invoice.getId(), invoice.balanceDue(),
                    "Invoice " + invoice.getNumber(),
                    invoice.getBillTo() == null ? null : invoice.getBillTo().getEmail(),
                    payUrl + "?status=success", payUrl + "?status=cancelled"));
            sessions.save(new CheckoutSession(invoice.getOrganizationId(), invoice.getId(), gateway.name(),
                    checkout.providerSessionId(), invoice.balanceDue(), checkout.url()));
            return checkout.url();
        });
    }

    // ---- the mock provider's hosted checkout page ----

    public MockCheckoutView mockCheckout(String sessionId) {
        CheckoutSession session = mockSession(sessionId);
        return runner.inOrganization(session.getOrganizationId(), () -> {
            Invoice invoice = invoiceService.load(session.getInvoiceId());
            String token = links.getOrCreate(PublicLink.Kind.INVOICE, invoice.getId()).getToken();
            return new MockCheckoutView(organizations.currentEntity().getName(), invoice.getNumber(),
                    MoneyDto.from(session.getAmount()), session.getStatus().name(), token);
        });
    }

    /** The payer clicked "Pay" on the mock checkout: the mock provider emits its signed webhook. */
    public MockPaymentGateway.SignedEvent mockComplete(String sessionId) {
        CheckoutSession session = mockSession(sessionId);
        if (session.getStatus() != CheckoutSession.Status.OPEN) {
            throw ApiException.conflict("This checkout is already finished");
        }
        return ((MockPaymentGateway) gateway).completionEvent(session.getOrganizationId(), sessionId, session.getAmount());
    }

    private CheckoutSession mockSession(String sessionId) {
        if (!(gateway instanceof MockPaymentGateway)) {
            throw ApiException.notFound("Not found");
        }
        return sessions.findByProviderAndProviderSessionId(MockPaymentGateway.NAME, sessionId)
                .orElseThrow(() -> ApiException.notFound("Checkout not found"));
    }

    /** Lets invoice emails include a "Pay online" button. */
    @Configuration
    static class EmailLinks {
        @Bean
        PayLinkProvider payLinkProvider(PaymentLinkService service) {
            return service::payUrlFor;
        }
    }
}
