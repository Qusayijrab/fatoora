package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.auth.User;
import com.fatoora.invoicing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Links a user to an organization with a role.
 *
 * <p>Not a {@code TenantScopedEntity}: "which orgs am I in?" must look across all orgs.
 * Every org-specific membership query takes the org id from {@code TenantContext}
 * explicitly instead (see {@link MembershipService}).
 */
@Entity
@Table(name = "memberships")
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected Membership() {}

    public Membership(Organization organization, User user, Role role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    public Organization getOrganization() {
        return organization;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    void changeRole(Role newRole) {
        this.role = newRole;
    }
}
