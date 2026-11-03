package com.fatoora.invoicing.common.tenancy;

import com.fatoora.invoicing.common.error.ApiException;
import java.util.Optional;

/**
 * Holds the current request's tenant. {@link TenantFilter} sets it once per request
 * and always clears it afterwards.
 *
 * <p>This is the single source of "which organization am I in?". Code never reads an
 * organization id from the request body, path or query string.
 */
public final class TenantContext {

    private static final ThreadLocal<CurrentTenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(CurrentTenant tenant) {
        CURRENT.set(tenant);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<CurrentTenant> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current tenant, or 403 if the caller has no active organization. */
    public static CurrentTenant require() {
        CurrentTenant tenant = CURRENT.get();
        if (tenant == null) {
            throw ApiException.forbidden("No active organization. Switch to an organization you belong to.");
        }
        return tenant;
    }
}
