package com.fatoora.invoicing.auth.dto;

import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.common.validation.MaxUtf8Bytes;
import com.fatoora.invoicing.common.validation.TimeZoneId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sign-up creates the user and their first organization together.
 * The password limit is 72 UTF-8 bytes, BCrypt's maximum (fewer characters in Arabic).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 8) @MaxUtf8Bytes(72) String password,
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
