package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.common.validation.CountryCode;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.common.validation.TimeZoneId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Changing the currency or time zone affects new documents only; issued invoices keep their snapshot.
 * Reminder fields are optional: null leaves them unchanged.
 */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @CurrencyCode String defaultCurrency,
        @NotNull @TimeZoneId String timeZone,
        @Size(max = 200) String legalName,
        @Size(max = 50) String taxId,
        @Email @Size(max = 254) String email,
        @Size(max = 50) String phone,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 100) String city,
        @CountryCode String country,
        Boolean remindersEnabled,
        @Size(max = 6) List<@Min(0) @Max(365) Integer> reminderDays) {}
