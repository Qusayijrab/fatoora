package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stripe Checkout over its REST API (no SDK needed): one session per payment, and the
 * "checkout.session.completed" webhook to record the money.
 *
 * <p>Regional detail: Stripe treats BHD, JOD, KWD, OMR and TND as three-decimal currencies
 * but requires the last digit to be 0 (it charges to the 10 fils). Amounts like
 * JOD 348.005 are refused up front instead of being silently rounded.
 */
public class StripePaymentGateway implements PaymentGateway {

    public static final String NAME = "stripe";
    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);
    private static final Set<String> TEN_FILS_CURRENCIES = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private final RestClient http;
    private final String webhookSecret;
    private final JsonMapper json;
    private final Clock clock;

    public StripePaymentGateway(String apiBase, String secretKey, String webhookSecret, JsonMapper json, Clock clock) {
        // Bounded: these calls run while the invoice row is locked.
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(5));
        requests.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .requestFactory(requests)
                .baseUrl(apiBase)
                .defaultHeaders(h -> h.setBasicAuth(secretKey, ""))
                .build();
        this.webhookSecret = webhookSecret;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Checkout createCheckout(CheckoutRequest r) {
        Money amount = r.amount();
        if (TEN_FILS_CURRENCIES.contains(amount.currency()) && amount.amountMinor() % 10 != 0) {
            throw ApiException.conflict("Online payment in " + amount.currency()
                    + " must be a multiple of 0.010. Settle the last fils by another method.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", r.successUrl());
        form.add("cancel_url", r.cancelUrl());
        form.add("client_reference_id", r.invoiceId().toString());
        if (r.expiresAt() != null) {
            form.add("expires_at", Long.toString(r.expiresAt().getEpochSecond()));
        }
        form.add("metadata[organization_id]", r.organizationId().toString());
        form.add("metadata[invoice_id]", r.invoiceId().toString());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", amount.currency().toLowerCase(Locale.ROOT));
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amount.amountMinor()));
        form.add("line_items[0][price_data][product_data][name]", r.description());
        if (r.customerEmail() != null) {
            form.add("customer_email", r.customerEmail());
        }
        try {
            String body = http.post().uri("/v1/checkout/sessions")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            JsonNode session = json.readTree(body);
            return new Checkout(session.path("id").asText(), session.path("url").asText());
        } catch (RestClientException e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "The payment provider is not reachable right now. Please try again.");
        }
    }

    @Override
    public boolean expireCheckout(String providerSessionId) {
        try {
            http.post().uri("/v1/checkout/sessions/{id}/expire", providerSessionId)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            // Already completed or expired, or Stripe is unreachable. If it was paid, the
            // completion webhook still arrives and is applied or flagged for a refund.
            log.warn("Could not expire Stripe checkout {}: {}", providerSessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhook(String payload, Map<String, String> headers) {
        TimestampedSignature.verify(webhookSecret, headers.get("stripe-signature"), payload, clock);
        try {
            JsonNode root = json.readTree(payload);
            String type = root.path("type").asText();
            JsonNode session = root.path("data").path("object");
            boolean completed = "checkout.session.completed".equals(type)
                    && "paid".equals(session.path("payment_status").asText());
            return new WebhookEvent(
                    root.path("id").asText(),
                    type,
                    completed,
                    completed ? UUID.fromString(session.path("metadata").path("organization_id").asText()) : null,
                    session.path("id").asText(null),
                    session.path("payment_intent").asText(null),
                    completed
                            ? Money.ofMinor(session.path("amount_total").asLong(),
                                    session.path("currency").asText().toUpperCase(Locale.ROOT))
                            : null);
        } catch (RuntimeException e) {
            throw new InvalidWebhookException("Unreadable payload");
        }
    }
}
