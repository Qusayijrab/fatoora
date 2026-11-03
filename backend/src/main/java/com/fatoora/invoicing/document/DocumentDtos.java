package com.fatoora.invoicing.document;

import com.fatoora.invoicing.common.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Shapes shared by quotes, invoices and credit notes. */
public final class DocumentDtos {

    private DocumentDtos() {}

    public static final String QUANTITY_PATTERN = "^\\d{1,9}(\\.\\d{1,3})?$";
    public static final String AMOUNT_PATTERN = "^\\d{1,13}(\\.\\d{1,3})?$";

    /** One line as typed. Amounts and quantities are decimal strings, never floats. */
    public record LineInput(
            UUID productId,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @Pattern(regexp = QUANTITY_PATTERN, message = "must be a number like 1 or 2.5") String quantity,
            @Size(max = 20) String unit,
            @NotNull @Pattern(regexp = AMOUNT_PATTERN, message = "must be an amount like 25 or 25.500") String unitPrice,
            UUID taxRateId) {}

    public record LinesInput(@NotEmpty(message = "add at least one line") @Size(max = 200) List<@Valid LineInput> lines) {}

    public record LineResponse(
            UUID id,
            int position,
            UUID productId,
            String description,
            String quantity,
            String unit,
            MoneyDto unitPrice,
            UUID taxRateId,
            String taxRate,
            MoneyDto subtotal,
            MoneyDto tax,
            MoneyDto total) {

        public static LineResponse from(LineFields l, String currency) {
            return new LineResponse(l.getId(), l.getPosition(), l.getProductId(), l.getDescription(),
                    l.getQuantity().stripTrailingZeros().toPlainString(), l.getUnit(),
                    MoneyDto.from(l.unitPrice(currency)), l.getTaxRateId(),
                    l.getTaxRate().stripTrailingZeros().toPlainString(), MoneyDto.from(l.subtotal(currency)),
                    MoneyDto.from(l.tax(currency)), MoneyDto.from(l.total(currency)));
        }
    }

    public record PartyResponse(
            String name, String email, String phone, String taxId, String addressLine1, String addressLine2,
            String city, String region, String postalCode, String country) {

        public static PartyResponse from(Party p) {
            if (p == null || p.getName() == null) {
                return null;
            }
            return new PartyResponse(p.getName(), p.getEmail(), p.getPhone(), p.getTaxId(), p.getAddressLine1(),
                    p.getAddressLine2(), p.getCity(), p.getRegion(), p.getPostalCode(), p.getCountry());
        }
    }

    public record ClientRef(UUID id, String name) {}

    public record DocumentRef(UUID id, String number) {}
}
