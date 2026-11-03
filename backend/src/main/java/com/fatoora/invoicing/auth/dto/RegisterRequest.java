package com.fatoora.invoicing.auth.dto;

import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.common.validation.TimeZoneId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sign-up creates the user and their first organization together.
 * Password max is 72 because BCrypt ignores anything after 72 bytes.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Size(max = 200) String organizationName,
        @NotNull @CurrencyCode String defaultCurrency,
        @NotNull @TimeZoneId String timeZone) {

    /** Never log or print the password. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", organizationName=" + organizationName + "]";
    }
}
