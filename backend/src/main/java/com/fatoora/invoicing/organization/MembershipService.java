package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.auth.User;
import com.fatoora.invoicing.auth.UserRepository;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.dto.MemberResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages members of the *current* organization only. The org id always comes from
 * {@link TenantContext}; a membership id from another org simply isn't found (404).
 */
@Service
public class MembershipService {

    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    public MembershipService(
            MembershipRepository memberships, OrganizationRepository organizations, UserRepository users) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        UUID orgId = TenantContext.require().organizationId();
        return memberships.findAllInOrganization(orgId).stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public MemberResponse add(String email, Role role) {
        CurrentTenant tenant = TenantContext.require();
        MembershipPolicy.checkCanAdd(tenant.role(), role);

        Organization org = lockCurrentOrganization(tenant);
        User user = users.findByEmail(User.normalizeEmail(email))
                .orElseThrow(() -> ApiException.notFound("No user is registered with that email"));
        if (memberships.existsInOrganization(org.getId(), user.getId())) {
            throw ApiException.conflict("That user is already a member of this organization");
        }
        return MemberResponse.from(memberships.save(new Membership(org, user, role)));
    }

    @Transactional
    public MemberResponse changeRole(UUID membershipId, Role newRole) {
        CurrentTenant tenant = TenantContext.require();
        lockCurrentOrganization(tenant);
        Membership membership = findInCurrentOrganization(membershipId, tenant);

        long owners = memberships.countByRole(tenant.organizationId(), Role.OWNER);
        MembershipPolicy.checkCanChangeRole(tenant.role(), membership.getRole(), newRole, owners);

        membership.changeRole(newRole);
        return MemberResponse.from(membership);
    }

    @Transactional
    public void remove(UUID membershipId) {
        CurrentTenant tenant = TenantContext.require();
        lockCurrentOrganization(tenant);
        Membership membership = findInCurrentOrganization(membershipId, tenant);

        long owners = memberships.countByRole(tenant.organizationId(), Role.OWNER);
        MembershipPolicy.checkCanRemove(tenant.role(), membership.getRole(), owners);

        memberships.delete(membership);
    }

    /** Serializes membership changes per org (see {@link OrganizationRepository#lockById}). */
    private Organization lockCurrentOrganization(CurrentTenant tenant) {
        return organizations.lockById(tenant.organizationId())
                .orElseThrow(() -> ApiException.notFound("Organization not found"));
    }

    private Membership findInCurrentOrganization(UUID membershipId, CurrentTenant tenant) {
        return memberships.findInOrganization(membershipId, tenant.organizationId())
                .orElseThrow(() -> ApiException.notFound("Member not found"));
    }
}
