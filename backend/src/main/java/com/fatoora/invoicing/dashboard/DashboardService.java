package com.fatoora.invoicing.dashboard;

import com.fatoora.invoicing.client.ClientRepository;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.MembershipRepository;
import com.fatoora.invoicing.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts that drive the onboarding route and the dashboard. Extended as features land. */
@Service
public class DashboardService {

    public record Summary(long members, long clients, long products) {}

    private final MembershipRepository memberships;
    private final ClientRepository clients;
    private final ProductRepository products;

    public DashboardService(MembershipRepository memberships, ClientRepository clients, ProductRepository products) {
        this.memberships = memberships;
        this.clients = clients;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        var tenant = TenantContext.require();
        return new Summary(
                memberships.findAllInOrganization(tenant.organizationId()).size(),
                clients.countByArchivedFalse(),
                products.countByArchivedFalse());
    }
}
