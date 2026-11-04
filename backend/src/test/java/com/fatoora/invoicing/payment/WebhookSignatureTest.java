package com.fatoora.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WebhookSignatureTest {

    static final String SECRET = "whsec_test_secret";
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String PAYLOAD = "{\"id\":\"evt_1\"}";

    @Test
    void validSignaturePasses() {
        String header = TimestampedSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD);
        assertThatCode(() -> TimestampedSignature.verify(SECRET, header, PAYLOAD, CLOCK)).doesNotThrowAnyException();
    }

    @Test
    void tamperedPayloadFails() {
        String header = TimestampedSignature.sign(SECRET, NOW.getEpochSecond(), PAYLOAD);
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, header, "{\"id\":\"evt_2\"}", CLOCK))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void wrongSecretFails() {
        String header = TimestampedSignature.sign("someone-else", NOW.getEpochSecond(), PAYLOAD);
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, header, PAYLOAD, CLOCK))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void oldSignaturesAreRejectedToStopReplays() {
        String header = TimestampedSignature.sign(SECRET, NOW.minusSeconds(600).getEpochSecond(), PAYLOAD);
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, header, PAYLOAD, CLOCK))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessageContaining("tolerance");
    }

    @Test
    void missingOrMalformedHeaderFails() {
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, null, PAYLOAD, CLOCK)).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, "garbage", PAYLOAD, CLOCK)).isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> TimestampedSignature.verify(SECRET, "t=abc,v1=00", PAYLOAD, CLOCK)).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void stripeCheckoutCompletedEventIsParsed() {
        UUID org = UUID.randomUUID();
        String payload = """
                {"id":"evt_123","type":"checkout.session.completed","data":{"object":{"id":"cs_test_1",
                "payment_status":"paid","payment_intent":"pi_1","amount_total":348000,"currency":"jod",
                "metadata":{"organization_id":"%s"}}}}""".formatted(org);
        var stripe = new StripePaymentGateway("http://localhost:1", "sk_test", SECRET, JsonMapper.builder().build(), CLOCK);

        var event = stripe.parseWebhook(payload,
                Map.of("stripe-signature", TimestampedSignature.sign(SECRET, NOW.getEpochSecond(), payload)));

        assertThat(event.completed()).isTrue();
        assertThat(event.eventId()).isEqualTo("evt_123");
        assertThat(event.providerSessionId()).isEqualTo("cs_test_1");
        assertThat(event.organizationId()).isEqualTo(org);
        assertThat(event.amount()).isEqualTo(Money.parse("348.000", "JOD"));
    }

    @Test
    void stripeRefusesJodAmountsThatAreNotWholeTensOfFils() {
        var stripe = new StripePaymentGateway("http://localhost:1", "sk_test", SECRET, JsonMapper.builder().build(), CLOCK);
        var request = new PaymentGateway.CheckoutRequest(UUID.randomUUID(), UUID.randomUUID(), Money.parse("348.005", "JOD"),
                "Invoice", null, "http://x/ok", "http://x/cancel", null);
        assertThatThrownBy(() -> stripe.createCheckout(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("multiple of 0.010");
    }

    @Test
    void stripeCheckoutsCarryAnExpiryAndCanBeExpired() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " " + body);
            boolean expire = exchange.getRequestURI().getPath().endsWith("/expire");
            int status = expire && exchange.getRequestURI().getPath().contains("cs_done") ? 400 : 200;
            byte[] out = """
                    {"id":"cs_test_9","url":"https://checkout.stripe.com/c/cs_test_9"}""".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        try {
            var stripe = new StripePaymentGateway("http://127.0.0.1:" + server.getAddress().getPort(), "sk_test", SECRET,
                    JsonMapper.builder().build(), CLOCK);
            Instant expiresAt = NOW.plusSeconds(3600);
            var checkout = stripe.createCheckout(new PaymentGateway.CheckoutRequest(UUID.randomUUID(), UUID.randomUUID(),
                    Money.parse("348.000", "JOD"), "Invoice", null, "http://x/ok", "http://x/cancel", expiresAt));
            assertThat(checkout.providerSessionId()).isEqualTo("cs_test_9");
            assertThat(seen.getFirst()).startsWith("POST /v1/checkout/sessions ")
                    .contains("expires_at=" + expiresAt.getEpochSecond());

            assertThat(stripe.expireCheckout("cs_test_9")).isTrue();
            assertThat(seen.get(1)).startsWith("POST /v1/checkout/sessions/cs_test_9/expire");
            // Already completed at Stripe: refused, reported as false rather than thrown.
            assertThat(stripe.expireCheckout("cs_done")).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
