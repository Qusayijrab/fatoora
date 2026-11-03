package com.fatoora.invoicing.tax;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public final class TaxRateDtos {

    private TaxRateDtos() {}

    public record TaxRateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 3) BigDecimal rate,
            boolean isDefault) {}

    /** {@code rate} is a plain decimal string such as "16" or "7.5". */
    public record TaxRateResponse(UUID id, String name, String rate, boolean isDefault, boolean archived) {

        public static TaxRateResponse from(TaxRate t) {
            return t == null ? null : new TaxRateResponse(t.getId(), t.getName(), t.getRate().stripTrailingZeros().toPlainString(), t.isDefault(), t.isArchived());
        }
    }
}
