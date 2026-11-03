package com.fatoora.invoicing.common.tenancy;

import com.fatoora.invoicing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base class for business data owned by one organization (clients, products, invoices...).
 *
 * <p>{@code @TenantId} is the "one central place" for tenancy. Hibernate then:
 * <ul>
 *   <li>fills {@code organization_id} from {@link TenantContext} on insert, and</li>
 *   <li>adds {@code organization_id = <current org>} to every query and load.</li>
 * </ul>
 * Nobody has to remember a WHERE clause, so nobody can forget one.
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    public UUID getOrganizationId() {
        return organizationId;
    }
}
