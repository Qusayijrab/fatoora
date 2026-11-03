package com.fatoora.invoicing.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidatorsTest {

    private final CurrencyCode.Validator currency = new CurrencyCode.Validator();
    private final TimeZoneId.Validator timeZone = new TimeZoneId.Validator();

    @ParameterizedTest
    @ValueSource(strings = {"USD", "JOD", "JPY", "EUR", "SAR"})
    void acceptsIsoCurrencies(String code) {
        assertThat(currency.isValid(code, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "US", "USDD", "XYZ", "", "12$"})
    void rejectsInvalidCurrencies(String code) {
        assertThat(currency.isValid(code, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Amman", "Europe/London", "America/New_York"})
    void acceptsIanaZones(String zone) {
        assertThat(timeZone.isValid(zone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+03:00", "GMT+3", "Mars/Olympus", "", "asia/amman"})
    void rejectsOffsetsAndUnknownZones(String zone) {
        assertThat(timeZone.isValid(zone, null)).isFalse();
    }
}
