package com.fatoora.invoicing.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Daily European Central Bank reference rates (via the free Frankfurter API), as units per
 * 1 USD. Used only for currencies without a manual rate or an official USD peg (EUR, GBP,
 * TRY, JPY, EGP...). Cached per day in memory. Disabled with app.fx.ecb-enabled=false.
 */
public class EcbRates {

    private static final Logger log = LoggerFactory.getLogger(EcbRates.class);

    private final RestClient http;
    private final JsonMapper json;
    private final Map<LocalDate, Map<String, BigDecimal>> cache = new ConcurrentHashMap<>();

    public EcbRates(String baseUrl, JsonMapper json) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.json = json;
    }

    public Optional<BigDecimal> perUsd(String currency, LocalDate date) {
        Map<String, BigDecimal> rates = cache.computeIfAbsent(date, this::fetch);
        return Optional.ofNullable(rates.get(currency));
    }

    private Map<String, BigDecimal> fetch(LocalDate date) {
        try {
            String body = http.get().uri("/v1/{date}?base=USD", date).retrieve().body(String.class);
            JsonNode rates = json.readTree(body).path("rates");
            Map<String, BigDecimal> out = new ConcurrentHashMap<>();
            for (var entry : rates.properties()) {
                out.put(entry.getKey(), entry.getValue().decimalValue());
            }
            out.put("USD", BigDecimal.ONE);
            return out;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("ECB rates for {} unavailable: {}", date, e.getMessage());
            return Map.of();
        }
    }
}
