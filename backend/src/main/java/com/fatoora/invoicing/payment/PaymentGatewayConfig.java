package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.mail.MailProperties;
import java.time.Clock;
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
        log.info("Payments: built-in mock checkout (no real money moves)");
        return new MockPaymentGateway(props.mock().webhookSecret(), urls, json, clock);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
