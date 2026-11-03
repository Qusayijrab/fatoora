package com.fatoora.invoicing.common.tenancy;

import com.fatoora.invoicing.organization.Role;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work as the system inside one organization, for flows that have no logged-in user:
 * public payment pages, webhooks and scheduled jobs.
 *
 * <p>The tenant is set before the transaction opens, so Hibernate's @TenantId filter is
 * bound to that organization for the whole unit of work, exactly like a user request.
 * The previous context is always restored.
 */
@Component
public class TenantRunner {

    private final TransactionTemplate tx;

    public TenantRunner(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Executes {@code work} in a new transaction as a system actor (no user id, owner rights). */
    public <T> T inOrganization(UUID organizationId, Supplier<T> work) {
        CurrentTenant previous = TenantContext.current().orElse(null);
        TenantContext.set(new CurrentTenant(organizationId, null, Role.OWNER));
        try {
            return tx.execute(status -> work.get());
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }

    public void inOrganization(UUID organizationId, Runnable work) {
        inOrganization(organizationId, () -> {
            work.run();
            return null;
        });
    }
}
