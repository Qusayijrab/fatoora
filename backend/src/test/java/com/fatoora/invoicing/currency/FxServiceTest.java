package com.fatoora.invoicing.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class FxServiceTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    @SuppressWarnings("unchecked")
    FxService service(ExchangeRateRepository repo, EcbRates ecb) {
        ObjectProvider<EcbRates> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ecb);
        return new FxService(repo, provider);
    }

    ExchangeRateRepository noManualRates() {
        ExchangeRateRepository repo = mock(ExchangeRateRepository.class);
        when(repo.findFirstByCurrencyAndBaseCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDescCreatedAtDesc(
                anyString(), anyString(), any())).thenReturn(Optional.empty());
        return repo;
    }

    @Test
    void sameCurrencyIsOne() {
        assertThat(service(noManualRates(), null).find("JOD", "JOD", DAY)).contains(new FxService.Rate(BigDecimal.ONE, "SAME"));
    }

    @Test
    void officialPegsGiveExactCrossRates() {
        FxService fx = service(noManualRates(), null);
        assertThat(fx.find("USD", "JOD", DAY).orElseThrow().value()).isEqualByComparingTo("0.709");
        assertThat(fx.find("USD", "JOD", DAY).orElseThrow().source()).isEqualTo("PEG");
        // 1 SAR = 0.709 / 3.75 JOD
        assertThat(fx.find("SAR", "JOD", DAY).orElseThrow().value()).isEqualByComparingTo("0.1890666667");
        // 1 JOD = 1 / 0.709 USD
        assertThat(fx.find("JOD", "USD", DAY).orElseThrow().value()).isEqualByComparingTo("1.4104372355");
    }

    @Test
    void aManualRateBeatsThePeg() {
        ExchangeRateRepository repo = mock(ExchangeRateRepository.class);
        when(repo.findFirstByCurrencyAndBaseCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDescCreatedAtDesc(
                "USD", "JOD", DAY)).thenReturn(Optional.of(new ExchangeRate("USD", "JOD", new BigDecimal("0.71"), DAY)));
        var rate = service(repo, null).find("USD", "JOD", DAY).orElseThrow();
        assertThat(rate.value()).isEqualByComparingTo("0.71");
        assertThat(rate.source()).isEqualTo("MANUAL");
    }

    @Test
    void ecbFillsTheGapsCrossedThroughUsd() {
        EcbRates ecb = mock(EcbRates.class);
        when(ecb.perUsd("EUR", DAY)).thenReturn(Optional.of(new BigDecimal("0.92")));
        var rate = service(noManualRates(), ecb).find("EUR", "JOD", DAY).orElseThrow();
        // 1 EUR = 0.709 / 0.92 JOD
        assertThat(rate.value()).isEqualByComparingTo("0.7706521739");
        assertThat(rate.source()).isEqualTo("ECB");
    }

    @Test
    void noSourceMeansNoRate() {
        assertThat(service(noManualRates(), null).find("EUR", "JOD", DAY)).isEmpty();
    }
}
