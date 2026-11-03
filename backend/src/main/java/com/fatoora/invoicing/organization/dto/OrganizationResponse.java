package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.organization.Role;
import java.util.UUID;

/** An organization as seen by one user, including that user's role in it and its seller details. */
public record OrganizationResponse(
        UUID id,
        String name,
        String defaultCurrency,
        String timeZone,
        Role role,
        String legalName,
        String taxId,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String country,
        boolean remindersEnabled,
        java.util.List<Integer> reminderDays) {

    public static OrganizationResponse from(Organization org, Role role) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getDefaultCurrency(), org.getTimeZone(), role,
                org.getLegalName(), org.getTaxId(), org.getEmail(), org.getPhone(), org.getAddressLine1(),
                org.getAddressLine2(), org.getCity(), org.getCountry(), org.isRemindersEnabled(), org.getReminderDays());
    }
}
