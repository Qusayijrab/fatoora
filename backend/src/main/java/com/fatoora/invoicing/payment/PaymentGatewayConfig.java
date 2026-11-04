package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.mail.MailProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** Picks the payment provider from {@code app.payments.provider}. */
@Configuration
public class PaymentGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayConfig.class);

    @Bean
    PaymentGateway paymentGateway(PaymentsProperties props, MailProperties urls, JsonMapper json, Clock clock) {
        if (StripePaymentGateway.NAME.equals(props.provider())) {
            var stripe = props.stripe();
            if (stripe == null || isBlank(stripe.secretKey()) || isBlank(stripe.webhookSecret())) {
                throw new IllegalStateException("app.payments.provider=stripe needs STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET");
            }
            log.info("Payments: Stripe Checkout");
            return new StripePaymentGateway(
                    isBlank(stripe.apiBase()) ? "https://api.stripe.com" : stripe.apiBase(),
                    stripe.secretKey(), stripe.webhookSecret(), json, clock);
        }
        if (!MockPaymentGateway.NAME.equals(props.provider())) {
            throw new IllegalStateException("Unknown app.payments.provider '" + props.provider() + "' (use stripe or mock)");
        }
        var mock = props.mock();
        if (mock == null || !mock.enabled()) {
            throw new IllegalStateException("app.payments.provider=mock is a fake checkout: anyone with a pay link can mark "
                    + "an invoice paid. Set PAYMENTS_PROVIDER=stripe, or PAYMENTS_MOCK_ENABLED=true for a demo.");
        }
        log.warn("Payments: built-in MOCK checkout. No real money moves; do not use with real clients.");
        String secret = isBlank(mock.webhookSecret()) ? randomSecret() : mock.webhookSecret();
        return new MockPaymentGateway(secret, urls, json, clock);
    }

    /** Only this app signs mock events, so a per-start secret is enough and nothing guessable is committed. */
    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
