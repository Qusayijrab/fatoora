package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.auth.User;
import com.fatoora.invoicing.auth.UserRepository;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.dto.OrganizationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;

    public OrganizationService(
            OrganizationRepository organizations, MembershipRepository memberships, UserRepository users) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
    }

    /** Creates an org and makes {@code owner} its first OWNER, in the caller's transaction. */
    @Transactional
    public Membership createWithOwner(User owner, String name, String defaultCurrency, String timeZone) {
        Organization org = organizations.save(new Organization(name.trim(), defaultCurrency, timeZone));
        return memberships.save(new Membership(org, owner, Role.OWNER));
    }

    @Transactional
    public OrganizationResponse createForUser(UUID userId, String name, String defaultCurrency, String timeZone) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        Membership owner = createWithOwner(user, name, defaultCurrency, timeZone);
        return OrganizationResponse.from(owner.getOrganization(), owner.getRole());
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listForUser(UUID userId) {
        return memberships.findAllForUser(userId).stream()
                .map(m -> OrganizationResponse.from(m.getOrganization(), m.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse current() {
        CurrentTenant tenant = TenantContext.require();
        Organization org = organizations.findById(tenant.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
        return OrganizationResponse.from(org, tenant.role());
    }
}
