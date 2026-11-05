package com.fatoora.invoicing.currency;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Daily European Central Bank reference rates (via the free Frankfurter API), as units per
 * 1 USD. Used only for currencies without a manual rate or an official USD peg (EUR, GBP,
 * TRY, JPY, EGP...). Disabled with app.fx.ecb-enabled=false.
 *
 * <p>Only successful answers are cached (per day). A failure is remembered for
 * {@link #RETRY_AFTER} so a provider outage doesn't make every request wait for a timeout,
 * and then the next request tries again: an outage blocks issuing for a minute, not until
 * the app restarts. Every call has connect and read timeouts.
 */
public class EcbRates {

    private static final Logger log = LoggerFactory.getLogger(EcbRates.class);

    static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    private final RestClient http;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<LocalDate, Map<String, BigDecimal>> cache = new ConcurrentHashMap<>();
    private final Map<LocalDate, Instant> failedAt = new ConcurrentHashMap<>();

    public EcbRates(String baseUrl, JsonMapper json, Clock clock) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(requests).baseUrl(baseUrl).build();
        this.json = json;
        this.clock = clock;
    }

    public Optional<BigDecimal> perUsd(String currency, LocalDate date) {
        return rates(date).map(r -> r.get(currency));
    }

    private Optional<Map<String, BigDecimal>> rates(LocalDate date) {
        Map<String, BigDecimal> cached = cache.get(date);
        if (cached != null) {
            return Optional.of(cached);
        }
        Instant failed = failedAt.get(date);
        if (failed != null && clock.instant().isBefore(failed.plus(RETRY_AFTER))) {
            return Optional.empty();
        }
        Optional<Map<String, BigDecimal>> fetched = fetch(date);
        if (fetched.isPresent()) {
            cache.put(date, fetched.get());
            failedAt.remove(date);
        } else {
            failedAt.put(date, clock.instant());
        }
        return fetched;
    }

    private Optional<Map<String, BigDecimal>> fetch(LocalDate date) {
        try {
            String body = http.get().uri("/v1/{date}?base=USD", date).retrieve().body(String.class);
            JsonNode rates = json.readTree(body).path("rates");
            Map<String, BigDecimal> out = new ConcurrentHashMap<>();
            for (var entry : rates.properties()) {
                out.put(entry.getKey(), entry.getValue().decimalValue());
            }
            if (out.isEmpty()) {
                log.warn("ECB rates for {} came back empty", date);
                return Optional.empty();
            }
            out.put("USD", BigDecimal.ONE);
            return Optional.of(out);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("ECB rates for {} unavailable: {}", date, e.getMessage());
            return Optional.empty();
        }
    }
}
