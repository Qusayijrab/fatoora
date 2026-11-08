package com.fatoora.invoicing.mail;

import com.fatoora.invoicing.common.error.ApiException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Emails to clients go out from our sender address, so an organization may only send
 * them once one of its owners has confirmed their own email address. Applies to emails
 * people send and to scheduled ones (recurring invoices, reminders) alike.
 */
@Component
public class ClientEmailPolicy {

    static final String NOT_VERIFIED = "Confirm your email address before sending emails to clients. "
            + "Use the link we sent you when you signed up (you can ask for a new one at the top of the page).";

    private final JdbcTemplate jdbc;

    public ClientEmailPolicy(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean canEmailClients(UUID organizationId) {
        Boolean ok = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM memberships m JOIN users u ON u.id = m.user_id
                               WHERE m.organization_id = ? AND m.role = 'OWNER' AND u.email_verified_at IS NOT NULL)
                """, Boolean.class, organizationId);
        return Boolean.TRUE.equals(ok);
    }

    public void requireCanEmailClients(UUID organizationId) {
        if (!canEmailClients(organizationId)) {
            throw ApiException.forbidden(NOT_VERIFIED);
        }
    }
}
