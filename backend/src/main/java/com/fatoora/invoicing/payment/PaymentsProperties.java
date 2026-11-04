package com.fatoora.invoicing.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.payments.*}. provider = stripe (needs secret-key and webhook-secret) or mock
 * (a built-in test checkout that sends real signed webhooks; refuses to start unless
 * {@code mock.enabled} is true, because it lets anyone with a pay link mark an invoice paid).
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentsProperties(String provider, Mock mock, Stripe stripe) {

    public record Mock(boolean enabled, String webhookSecret) {}

    public record Stripe(String secretKey, String webhookSecret, String apiBase) {}
}
