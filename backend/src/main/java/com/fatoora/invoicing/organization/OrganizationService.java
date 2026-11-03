package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.audit.AuditService;
import com.fatoora.invoicing.auth.User;
import com.fatoora.invoicing.auth.UserRepository;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.dto.OrganizationResponse;
import com.fatoora.invoicing.organization.dto.UpdateOrganizationRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AuditService audit;

    public OrganizationService(OrganizationRepository organizations, MembershipRepository memberships,
            UserRepository users, AuditService audit) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
    }

    /** Creates an org and makes {@code owner} its first OWNER, in the caller's transaction. */
    @Transactional
    public Membership createWithOwner(User owner, String name, String defaultCurrency, String timeZone) {
        Organization org = organizations.save(new Organization(name.trim(), defaultCurrency, timeZone));
        Membership membership = memberships.save(new Membership(org, owner, Role.OWNER));
        audit.record(org.getId(), owner.getId(), "organization", org.getId(), "created",
                "Organization " + org.getName() + " created", Map.of("currency", defaultCurrency));
        return membership;
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

    @Transactional
    public OrganizationResponse updateCurrent(UpdateOrganizationRequest request) {
        CurrentTenant tenant = TenantContext.require().requireRole(Role.ADMIN);
        Organization org = organizations.findById(tenant.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
        org.update(request);
        audit.record("organization", org.getId(), "updated", "Organization details updated", Map.of());
        return OrganizationResponse.from(org, tenant.role());
    }

    /** The current organization entity (time zone, seller details), for document services. */
    @Transactional(readOnly = true)
    public Organization currentEntity() {
        return organizations.findById(TenantContext.require().organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
    }

    /** "Today" in the organization's own time zone: due dates and overdue use this, not the server clock. */
    public java.time.LocalDate today(Organization org, java.time.Clock clock) {
        return java.time.LocalDate.now(clock.withZone(java.time.ZoneId.of(org.getTimeZone())));
    }
}
