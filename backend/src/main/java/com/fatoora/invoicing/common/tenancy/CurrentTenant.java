package com.fatoora.invoicing.common.tenancy;

import com.fatoora.invoicing.organization.Role;
import java.util.UUID;

/**
 * Who is making this request, and in which organization.
 *
 * @param organizationId the active org (from the signed JWT, verified against memberships)
 * @param userId the authenticated user
 * @param role the user's role in that org, read fresh from the database on each request
 */
public record CurrentTenant(UUID organizationId, UUID userId, Role role) {}
