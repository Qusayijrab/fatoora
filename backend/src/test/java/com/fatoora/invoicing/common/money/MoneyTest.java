package com.fatoora.invoicing.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.error.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({
        "JOD, 3", "KWD, 3", "BHD, 3", "OMR, 3",
        "USD, 2", "SAR, 2", "AED, 2", "EUR, 2",
        "JPY, 0",
    })
    void decimalsFollowIso4217(String currency, int decimals) {
        assertThat(Money.decimalsOf(currency)).isEqualTo(decimals);
    }

    @Test
    void parsesIntoMinorUnits() {
        assertThat(Money.parse("1250.5", "JOD").amountMinor()).isEqualTo(1_250_500);
        assertThat(Money.parse("1250.500", "JOD").amountMinor()).isEqualTo(1_250_500);
        assertThat(Money.parse("19.99", "USD").amountMinor()).isEqualTo(1999);
        assertThat(Money.parse("1251", "JPY").amountMinor()).isEqualTo(1251);
        assertThat(Money.parse("0", "USD").isZero()).isTrue();
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyHas() {
        assertThatThrownBy(() -> Money.parse("1.005", "USD")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("1.0005", "JOD")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("10.5", "JPY")).isInstanceOf(ApiException.class);
    }

    @Test
    void trailingZerosBeyondTheCurrencyAreFine() {
        assertThat(Money.parse("10.50", "USD").amountMinor()).isEqualTo(1050);
        assertThat(Money.parse("10.000", "JPY").amountMinor()).isEqualTo(10);
    }

    @Test
    void rejectsGarbageAndUnknownCurrencies() {
        assertThatThrownBy(() -> Money.parse("abc", "USD")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.parse("1", "XYZ")).isInstanceOf(ApiException.class);
    }

    @Test
    void printsWithExactlyTheCurrencyDecimals() {
        assertThat(Money.ofMinor(1_250_500, "JOD").toPlainString()).isEqualTo("1250.500");
        assertThat(Money.ofMinor(5, "USD").toPlainString()).isEqualTo("0.05");
        assertThat(Money.ofMinor(1251, "JPY").toPlainString()).isEqualTo("1251");
        assertThat(Money.ofMinor(-150, "USD").toPlainString()).isEqualTo("-1.50");
    }

    @Test
    void timesRoundsHalfUpToTheMinorUnit() {
        // 0.333 JOD x 1.5 = 0.4995 -> 0.500
        assertThat(Money.parse("0.333", "JOD").times(new BigDecimal("1.5")).toPlainString()).isEqualTo("0.500");
        // 0.01 USD x 0.5 = 0.005 -> 0.01 (half up)
        assertThat(Money.parse("0.01", "USD").times(new BigDecimal("0.5")).toPlainString()).isEqualTo("0.01");
        assertThat(Money.parse("25.000", "JOD").times(new BigDecimal("12")).toPlainString()).isEqualTo("300.000");
    }

    @Test
    void percentRoundsHalfUp() {
        assertThat(Money.parse("1150.000", "JOD").percent(new BigDecimal("16")).toPlainString()).isEqualTo("184.000");
        // 0.25 USD x 10% = 0.025 -> 0.03
        assertThat(Money.parse("0.25", "USD").percent(new BigDecimal("10")).toPlainString()).isEqualTo("0.03");
        // 0.24 USD x 10% = 0.024 -> 0.02
        assertThat(Money.parse("0.24", "USD").percent(new BigDecimal("10")).toPlainString()).isEqualTo("0.02");
        assertThat(Money.parse("100", "USD").percent(new BigDecimal("7.5")).toPlainString()).isEqualTo("7.50");
    }

    @Test
    void addsAndSubtractsInOneCurrencyOnly() {
        Money a = Money.parse("1.250", "JOD");
        Money b = Money.parse("0.750", "JOD");
        assertThat(a.plus(b).toPlainString()).isEqualTo("2.000");
        assertThat(a.minus(b).toPlainString()).isEqualTo("0.500");
        assertThatThrownBy(() -> a.plus(Money.parse("1", "USD"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsWithAGivenRateRoundingToTheTarget() {
        // 100.00 USD at 0.709 JOD per USD = 70.900 JOD
        assertThat(Money.parse("100", "USD").convert("JOD", new BigDecimal("0.709")).toPlainString()).isEqualTo("70.900");
        // 1.000 JOD at 1.41044 USD per JOD = 1.41044 -> 1.41 USD
        assertThat(Money.parse("1", "JOD").convert("USD", new BigDecimal("1.41044")).toPlainString()).isEqualTo("1.41");
        // to a zero-decimal currency
        assertThat(Money.parse("10.00", "USD").convert("JPY", new BigDecimal("149.55")).toPlainString()).isEqualTo("1496");
    }
}
