package com.fatoora.invoicing.product;

import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.tax.TaxRateDtos.TaxRateResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ProductDtos {

    private ProductDtos() {}

    /** A non-negative decimal with up to 3 decimals; the currency then decides how many are allowed. */
    public static final String AMOUNT_PATTERN = "^\\d{1,13}(\\.\\d{1,3})?$";

    public record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @Size(max = 64) String sku,
            @Size(max = 20) String unit,
            @NotNull @Pattern(regexp = AMOUNT_PATTERN, message = "must be an amount like 25 or 25.500") String unitPrice,
            @NotNull @CurrencyCode String currency,
            UUID taxRateId) {}

    public record ProductResponse(
            UUID id,
            String name,
            String description,
            String sku,
            String unit,
            MoneyDto unitPrice,
            TaxRateResponse taxRate,
            boolean archived) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getSku(), p.getUnit(),
                    MoneyDto.from(p.getUnitPrice()), TaxRateResponse.from(p.getTaxRate()), p.isArchived());
        }
    }
}
