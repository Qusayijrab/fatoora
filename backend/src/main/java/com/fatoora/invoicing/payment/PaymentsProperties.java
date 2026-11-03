package com.fatoora.invoicing.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.payments.*}. provider = mock (default: a built-in test checkout that sends
 * real signed webhooks) or stripe (needs secret-key and webhook-secret).
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentsProperties(String provider, Mock mock, Stripe stripe) {

    public record Mock(String webhookSecret) {}

    public record Stripe(String secretKey, String webhookSecret, String apiBase) {}
}
