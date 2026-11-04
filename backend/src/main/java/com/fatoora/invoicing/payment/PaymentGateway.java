package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.common.money.Money;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The only thing the rest of the app knows about a payment provider (CLAUDE.md rule 8).
 * Swap Stripe for Tap, PayTabs or HyperPay (common in Jordan and the Gulf) by adding
 * one implementation; nothing else changes.
 */
public interface PaymentGateway {

    /** Short id stored with payments and webhook events, e.g. "stripe", "mock". */
    String name();

    /** Starts a hosted checkout for one amount; returns where to send the payer. */
    Checkout createCheckout(CheckoutRequest request);

    /**
     * Closes a checkout so it can no longer take money. Returns false if the provider
     * refused (typically because the payer already completed it); the caller then relies
     * on the completion webhook, which is recorded and surfaced if it cannot be applied.
     */
    boolean expireCheckout(String providerSessionId);

    /**
     * Verifies the webhook signature and parses it. Throws {@link InvalidWebhookException}
     * for anything that is not provably from the provider.
     */
    WebhookEvent parseWebhook(String payload, Map<String, String> headers);

    record CheckoutRequest(
            UUID organizationId,
            UUID invoiceId,
            Money amount,
            String description,
            String customerEmail,
            String successUrl,
            String cancelUrl,
            Instant expiresAt) {}

    record Checkout(String providerSessionId, String url) {}

    /**
     * A provider event, normalized. {@code completed} is true for "money captured";
     * other event types are recorded and ignored.
     */
    record WebhookEvent(
            String eventId,
            String type,
            boolean completed,
            UUID organizationId,
            String providerSessionId,
            String providerPaymentId,
            Money amount) {}

    class InvalidWebhookException extends RuntimeException {
        public InvalidWebhookException(String message) {
            super(message);
        }
    }
}
