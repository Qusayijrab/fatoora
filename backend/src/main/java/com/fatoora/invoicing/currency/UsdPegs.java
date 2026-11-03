package com.fatoora.invoicing.currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Official central-bank pegs to the US dollar, as "units per 1 USD". These are fixed by
 * law, not market rates, so they are safe to hard-code and need no network:
 * Jordan 0.709, Saudi 3.75, UAE 3.6725, Qatar 3.64, Bahrain 0.376, Oman 0.3845.
 * (Kuwait pegs to an undisclosed basket, so KWD is not here.)
 */
public final class UsdPegs {

    private UsdPegs() {}

    private static final Map<String, BigDecimal> PER_USD = Map.of(
            "USD", BigDecimal.ONE,
            "JOD", new BigDecimal("0.709"),
            "SAR", new BigDecimal("3.75"),
            "AED", new BigDecimal("3.6725"),
            "QAR", new BigDecimal("3.64"),
            "BHD", new BigDecimal("0.376"),
            "OMR", new BigDecimal("0.3845"));

    public static Optional<BigDecimal> perUsd(String currency) {
        return Optional.ofNullable(PER_USD.get(currency));
    }
}
