package com.fatoora.invoicing.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query that touches one org's members takes the org id as a parameter. Callers
 * pass {@code TenantContext.require().organizationId()}, never a value from the request.
 */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("select m.role from Membership m where m.organization.id = :orgId and m.user.id = :userId")
    Optional<Role> findRole(@Param("orgId") UUID organizationId, @Param("userId") UUID userId);

    @Query("select m from Membership m join fetch m.user where m.organization.id = :orgId order by m.createdAt")
    List<Membership> findAllInOrganization(@Param("orgId") UUID organizationId);

    @Query("select m from Membership m join fetch m.user where m.id = :id and m.organization.id = :orgId")
    Optional<Membership> findInOrganization(@Param("id") UUID membershipId, @Param("orgId") UUID organizationId);

    @Query("select count(m) > 0 from Membership m where m.organization.id = :orgId and m.user.id = :userId")
    boolean existsInOrganization(@Param("orgId") UUID organizationId, @Param("userId") UUID userId);

    @Query("select count(m) from Membership m where m.organization.id = :orgId and m.role = :role")
    long countByRole(@Param("orgId") UUID organizationId, @Param("role") Role role);

    /** All orgs a user belongs to, oldest membership first. */
    @Query("select m from Membership m join fetch m.organization where m.user.id = :userId order by m.createdAt")
    List<Membership> findAllForUser(@Param("userId") UUID userId);
}
