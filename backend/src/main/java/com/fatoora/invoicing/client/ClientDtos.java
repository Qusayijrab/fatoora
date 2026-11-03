package com.fatoora.invoicing.client;

import com.fatoora.invoicing.common.validation.CountryCode;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {}

    public record ClientRequest(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 50) String phone,
            @Size(max = 50) String taxId,
            @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @Size(max = 100) String city,
            @Size(max = 100) String region,
            @Size(max = 20) String postalCode,
            @CountryCode String country,
            @CurrencyCode String currency,
            @Size(max = 5000) String notes) {}

    public record ClientResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String taxId,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String country,
            String currency,
            String notes,
            boolean archived,
            Instant createdAt) {

        public static ClientResponse from(Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getTaxId(),
                    c.getAddressLine1(), c.getAddressLine2(), c.getCity(), c.getRegion(), c.getPostalCode(),
                    c.getCountry(), c.getCurrency(), c.getNotes(), c.isArchived(), c.getCreatedAt());
        }
    }
}
