package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.mail.MailProperties;
import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stand-in payment provider for local development and demos. Its hosted checkout is a
 * page in this app ("/pay/mock/{session}") that clearly says no real money moves. When the
 * payer confirms, it produces a webhook signed exactly like Stripe's and feeds it through
 * the same webhook pipeline, so signature checks and idempotency are exercised for real.
 */
public class MockPaymentGateway implements PaymentGateway {

    public static final String NAME = "mock";
    public static final String SIGNATURE_HEADER = "x-mock-signature";

    private final String webhookSecret;
    private final MailProperties urls;
    private final JsonMapper json;
    private final Clock clock;

    public MockPaymentGateway(String webhookSecret, MailProperties urls, JsonMapper json, Clock clock) {
        this.webhookSecret = webhookSecret;
        this.urls = urls;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Checkout createCheckout(CheckoutRequest request) {
        String sessionId = "mock_cs_" + UUID.randomUUID().toString().replace("-", "");
        return new Checkout(sessionId, urls.publicUrl() + "/pay/mock/" + sessionId);
    }

    /** Nothing to do at the "provider": the mock checkout page checks our session row. */
    @Override
    public boolean expireCheckout(String providerSessionId) {
        return true;
    }

    /** What the "provider" sends when the payer completes the mock checkout. */
    public SignedEvent completionEvent(UUID organizationId, String sessionId, Money amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("organizationId", organizationId.toString());
        data.put("sessionId", sessionId);
        data.put("paymentId", "mock_pi_" + UUID.randomUUID().toString().replace("-", ""));
        data.put("amountMinor", amount.amountMinor());
        data.put("currency", amount.currency());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "mock_evt_" + UUID.randomUUID().toString().replace("-", ""));
        event.put("type", "checkout.completed");
        event.put("data", data);
        String payload = json.writeValueAsString(event);
        return new SignedEvent(payload, TimestampedSignature.sign(webhookSecret, clock.instant().getEpochSecond(), payload));
    }

    public record SignedEvent(String payload, String signatureHeader) {}

    @Override
    public WebhookEvent parseWebhook(String payload, Map<String, String> headers) {
        TimestampedSignature.verify(webhookSecret, headers.get(SIGNATURE_HEADER), payload, clock);
        try {
            JsonNode root = json.readTree(payload);
            JsonNode data = root.path("data");
            boolean completed = "checkout.completed".equals(root.path("type").asText());
            return new WebhookEvent(
                    root.path("id").asText(),
                    root.path("type").asText(),
                    completed,
                    completed ? UUID.fromString(data.path("organizationId").asText()) : null,
                    data.path("sessionId").asText(null),
                    data.path("paymentId").asText(null),
                    completed ? Money.ofMinor(data.path("amountMinor").asLong(), data.path("currency").asText()) : null);
        } catch (RuntimeException e) {
            throw new InvalidWebhookException("Unreadable payload");
        }
    }
}
