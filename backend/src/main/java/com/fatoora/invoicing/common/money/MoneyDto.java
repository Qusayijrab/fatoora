package com.fatoora.invoicing.common.money;

/**
 * Money on the wire. The amount is a decimal *string* ("1250.500") so JavaScript never
 * turns it into a binary float; {@code amountMinor} is there for exact client-side math.
 */
public record MoneyDto(String amount, String currency, long amountMinor) {

    public static MoneyDto from(Money money) {
        return money == null ? null : new MoneyDto(money.toPlainString(), money.currency(), money.amountMinor());
    }
}
