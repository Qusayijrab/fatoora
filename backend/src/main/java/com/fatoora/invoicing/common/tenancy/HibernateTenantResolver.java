package com.fatoora.invoicing.common.tenancy;

import java.util.Map;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which organization the current database session belongs to, so
 * {@code @TenantId} entities are filtered automatically.
 *
 * <p>With no tenant (e.g. during login) it returns an all-zero UUID that matches no
 * organization: tenant-scoped queries then find nothing, and inserts fail the
 * foreign key. Failing closed is safer than silently seeing everything.
 */
@Component
public class HibernateTenantResolver
        implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current().map(CurrentTenant::organizationId).orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.tenant_identifier_resolver", this);
    }
}
