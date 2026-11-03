package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.Role;
import java.util.UUID;

/** An organization as seen by one user, including that user's role in it. */
public record OrganizationResponse(UUID id, String name, String defaultCurrency, String timeZone, Role role) {

    public static OrganizationResponse from(Organization org, Role role) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getDefaultCurrency(), org.getTimeZone(), role);
    }
}
