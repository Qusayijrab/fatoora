package com.fatoora.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
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
                "Invoice", null, "http://x/ok", "http://x/cancel");
        assertThatThrownBy(() -> stripe.createCheckout(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("multiple of 0.010");
    }
}
