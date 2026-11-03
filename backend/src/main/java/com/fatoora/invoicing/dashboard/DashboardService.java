package com.fatoora.invoicing.dashboard;

import com.fatoora.invoicing.client.ClientRepository;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.invoice.InvoiceKind;
import com.fatoora.invoicing.invoice.InvoiceRepository;
import com.fatoora.invoicing.invoice.PaymentRepository;
import com.fatoora.invoicing.organization.MembershipRepository;
import com.fatoora.invoicing.product.ProductRepository;
import com.fatoora.invoicing.quote.QuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts that drive the onboarding route. Every repository here is tenant-scoped. */
@Service
public class DashboardService {

    public record Summary(long members, long clients, long products, long quotesSent, long invoicesIssued, long payments) {}

    private final MembershipRepository memberships;
    private final ClientRepository clients;
    private final ProductRepository products;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    public DashboardService(MembershipRepository memberships, ClientRepository clients, ProductRepository products,
            QuoteRepository quotes, InvoiceRepository invoices, PaymentRepository payments) {
        this.memberships = memberships;
        this.clients = clients;
        this.products = products;
        this.quotes = quotes;
        this.invoices = invoices;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        var tenant = TenantContext.require();
        return new Summary(
                memberships.findAllInOrganization(tenant.organizationId()).size(),
                clients.countByArchivedFalse(),
                products.countByArchivedFalse(),
                quotes.countByNumberIsNotNull(),
                invoices.countByKindAndNumberIsNotNull(InvoiceKind.INVOICE),
                payments.count());
    }
}
