package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.common.validation.TimeZoneId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @CurrencyCode String defaultCurrency,
        @NotNull @TimeZoneId String timeZone) {}
