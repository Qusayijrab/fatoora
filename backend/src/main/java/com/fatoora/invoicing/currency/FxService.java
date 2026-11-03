package com.fatoora.invoicing.currency;

import com.fatoora.invoicing.common.error.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the rate to freeze onto an invoice: "1 {@code currency} = rate {@code base}".
 * Sources, in order of trust:
 * <ol>
 *   <li>MANUAL: a rate the organization entered (latest one effective on the date);</li>
 *   <li>PEG: both currencies have an official USD peg (JOD, SAR, AED, QAR, BHD, OMR, USD);</li>
 *   <li>ECB: European Central Bank reference rates for that date, crossed through USD.</li>
 * </ol>
 * With no source, issuing is refused rather than guessing a number that would then be
 * frozen forever.
 */
@Service
public class FxService {

    public record Rate(BigDecimal value, String source) {}

    static final int SCALE = 10;

    private final ExchangeRateRepository manual;
    private final ObjectProvider<EcbRates> ecb;

    public FxService(ExchangeRateRepository manual, ObjectProvider<EcbRates> ecb) {
        this.manual = manual;
        this.ecb = ecb;
    }

    @Transactional(readOnly = true)
    public Rate rateFor(String currency, String base, LocalDate date) {
        return find(currency, base, date).orElseThrow(() -> ApiException.conflict(
                "No exchange rate from " + currency + " to " + base + " for " + date
                        + ". Add one under Settings → Exchange rates."));
    }

    @Transactional(readOnly = true)
    public Optional<Rate> find(String currency, String base, LocalDate date) {
        if (currency.equals(base)) {
            return Optional.of(new Rate(BigDecimal.ONE, "SAME"));
        }
        var entered = manual
                .findFirstByCurrencyAndBaseCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDescCreatedAtDesc(
                        currency, base, date);
        if (entered.isPresent()) {
            return Optional.of(new Rate(entered.get().getRate(), "MANUAL"));
        }
        var pegFrom = UsdPegs.perUsd(currency);
        var pegTo = UsdPegs.perUsd(base);
        if (pegFrom.isPresent() && pegTo.isPresent()) {
            return Optional.of(new Rate(cross(pegFrom.get(), pegTo.get()), "PEG"));
        }
        EcbRates rates = ecb.getIfAvailable();
        if (rates != null) {
            var from = pegFrom.or(() -> rates.perUsd(currency, date));
            var to = pegTo.or(() -> rates.perUsd(base, date));
            if (from.isPresent() && to.isPresent()) {
                return Optional.of(new Rate(cross(from.get(), to.get()), "ECB"));
            }
        }
        return Optional.empty();
    }

    /** Both as units per USD: 1 FROM = (to / from) TO. */
    static BigDecimal cross(BigDecimal fromPerUsd, BigDecimal toPerUsd) {
        return toPerUsd.divide(fromPerUsd, SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
