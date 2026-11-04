package com.fatoora.invoicing.common.money;

import com.fatoora.invoicing.common.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of one currency, stored as a whole number of its smallest unit
 * ("minor units": fils for JOD, cents for USD, yen for JPY). Never a float or double.
 *
 * <p>Decimals follow ISO 4217 via {@link Currency#getDefaultFractionDigits()}:
 * JOD/KWD/BHD/OMR = 3, USD/SAR/EUR = 2, JPY = 0. Every rounding is HALF_UP.
 *
 * <p>Immutable; arithmetic returns new values and refuses to mix currencies.
 */
public record Money(long amountMinor, String currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
        decimalsOf(currency); // validates the code
    }

    public static Money zero(String currency) {
        return new Money(0, currency);
    }

    public static Money ofMinor(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    /**
     * Parses a major-unit amount such as "1250.500" (JOD) or "19.99" (USD).
     * Rejects more decimals than the currency allows instead of silently rounding:
     * "1.005 USD" is a user error, not 1.01.
     */
    public static Money parse(String amount, String currency) {
        int decimals = decimalsOf(currency);
        BigDecimal value;
        try {
            value = new BigDecimal(amount.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw ApiException.badRequest("Not a valid amount: " + amount);
        }
        if (value.stripTrailingZeros().scale() > decimals) {
            throw ApiException.badRequest(
                    currency + " allows " + decimals + " decimal" + (decimals == 1 ? "" : "s") + ", got " + amount);
        }
        return new Money(value.movePointRight(decimals).longValueExact(), currency);
    }

    public static int decimalsOf(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return Math.max(digits, 0);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown currency: " + currency);
        }
    }

    public int decimals() {
        return decimalsOf(currency);
    }

    /** The amount in major units with exactly the currency's decimals, e.g. 1250.500. */
    public BigDecimal toMajor() {
        return BigDecimal.valueOf(amountMinor, decimals());
    }

    /** "1250.500" for JOD, "19.99" for USD, "1251" for JPY. Used in APIs (strings, never JS numbers). */
    public String toPlainString() {
        return toMajor().toPlainString();
    }

    /** "JOD 1,250.500": for text people read (audit log, messages). APIs use {@link #toPlainString()}. */
    public String toDisplayString() {
        String plain = toPlainString();
        boolean negative = plain.startsWith("-");
        String digits = negative ? plain.substring(1) : plain;
        int dot = digits.indexOf('.');
        String whole = dot < 0 ? digits : digits.substring(0, dot);
        String fraction = dot < 0 ? "" : digits.substring(dot);
        String grouped = whole.replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
        return currency + " " + (negative ? "-" : "") + grouped + fraction;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    /** Price x quantity, e.g. 25.000 JOD x 1.5 hours = 37.500 JOD. Rounded HALF_UP to the minor unit. */
    public Money times(BigDecimal quantity) {
        BigDecimal result = BigDecimal.valueOf(amountMinor).multiply(quantity).setScale(0, RoundingMode.HALF_UP);
        return new Money(result.longValueExact(), currency);
    }

    /** A percentage of this amount, e.g. 16% VAT. Rounded HALF_UP to the minor unit. */
    public Money percent(BigDecimal ratePercent) {
        BigDecimal result = BigDecimal.valueOf(amountMinor)
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return new Money(result.longValueExact(), currency);
    }

    /**
     * Converts to another currency at {@code rate} (1 unit of this = rate units of target),
     * rounding HALF_UP to the target's minor unit. Used for reporting with snapshotted rates.
     */
    public Money convert(String targetCurrency, BigDecimal rate) {
        BigDecimal major = toMajor().multiply(rate);
        int targetDecimals = decimalsOf(targetCurrency);
        long minor = major.setScale(targetDecimals, RoundingMode.HALF_UP).movePointRight(targetDecimals).longValueExact();
        return new Money(minor, targetCurrency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency + " " + toPlainString();
    }
}
