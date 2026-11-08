package com.fatoora.invoicing.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.rate-limits.*}: how often the abuse-prone actions may run. Login and sign-up
 * are limited per client IP (forwarded by the Next.js server, which makes every API
 * call), login also per email address, and emails a person sends per organization.
 */
@ConfigurationProperties(prefix = "app.rate-limits")
public record RateLimits(
        @DefaultValue("20") int loginsPerIpPer10Minutes,
        @DefaultValue("10") int loginsPerEmailPer15Minutes,
        @DefaultValue("5") int registrationsPerIpPerHour,
        @DefaultValue("60") int emailsPerOrganizationPerHour) {

    /**
     * The visitor's address: the first X-Forwarded-For entry, which the Next.js server
     * sets from the request it received; otherwise the TCP peer. Only the frontend is
     * exposed in production, so the header can't be set directly by a client of the API.
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String emailKey(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String orgKey(UUID organizationId) {
        return organizationId.toString();
    }
}
