package com.fatoora.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.mail.MailProperties;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** The fake checkout must never start by accident (it lets anyone with a pay link mark an invoice paid). */
class PaymentGatewayConfigTest {

    final PaymentGatewayConfig config = new PaymentGatewayConfig();
    final MailProperties urls = new MailProperties("invoices@example.com", "https://app.example.com");
    final JsonMapper json = JsonMapper.builder().build();
    final Clock clock = Clock.systemUTC();

    PaymentGateway gateway(String provider, boolean mockEnabled, String mockSecret) {
        return config.paymentGateway(new PaymentsProperties(provider, new PaymentsProperties.Mock(mockEnabled, mockSecret),
                new PaymentsProperties.Stripe(null, null, null)), urls, json, clock);
    }

    @Test
    void mockRefusesToStartUnlessExplicitlyEnabled() {
        assertThatThrownBy(() -> gateway("mock", false, "s")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENTS_MOCK_ENABLED");
        assertThat(gateway("mock", true, "s")).isInstanceOf(MockPaymentGateway.class);
    }

    @Test
    void unknownProvidersAndStripeWithoutKeysRefuseToStart() {
        assertThatThrownBy(() -> gateway("paypal", true, "s")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gateway("stripe", true, "s")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutAConfiguredSecretTheMockSignsWithARandomOneNobodyElseKnows() {
        var a = (MockPaymentGateway) gateway("mock", true, "");
        var b = (MockPaymentGateway) gateway("mock", true, "");
        var event = a.completionEvent(UUID.randomUUID(), "mock_cs_1", Money.parse("1", "JOD"));
        assertThat(a.parseWebhook(event.payload(), Map.of(MockPaymentGateway.SIGNATURE_HEADER, event.signatureHeader())))
                .isNotNull();
        // Each start picks its own secret, so a signature made elsewhere does not verify.
        assertThatThrownBy(() -> b.parseWebhook(event.payload(),
                Map.of(MockPaymentGateway.SIGNATURE_HEADER, event.signatureHeader())))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class);
    }
}
