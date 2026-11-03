package com.fatoora.invoicing.common.tenancy;

import com.fatoora.invoicing.organization.MembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs right after the JWT is verified. Reads the {@code org} claim, checks in the
 * database that the user is still a member of that org, and stores the result in
 * {@link TenantContext}.
 *
 * <p>Why check the database when the token is signed? So that removing someone from
 * an org (or changing their role) takes effect on their very next request, not when
 * their token expires.
 *
 * <p>If the membership is gone, the tenant is simply left empty: endpoints that need
 * an org answer 403, while endpoints like "list my orgs" or "switch org" still work.
 */
public class TenantFilter extends OncePerRequestFilter {

    public static final String ORG_CLAIM = "org";

    private final MembershipRepository memberships;

    public TenantFilter(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String orgClaim = jwtAuth.getToken().getClaimAsString(ORG_CLAIM);
                if (orgClaim != null) {
                    UUID organizationId = UUID.fromString(orgClaim);
                    UUID userId = UUID.fromString(jwtAuth.getName());
                    memberships
                            .findRole(organizationId, userId)
                            .ifPresent(role -> TenantContext.set(new CurrentTenant(organizationId, userId, role)));
                }
            }
            chain.doFilter(request, response);
        } finally {
            // Threads are reused across requests: never leak one request's tenant into the next.
            TenantContext.clear();
        }
    }
}
