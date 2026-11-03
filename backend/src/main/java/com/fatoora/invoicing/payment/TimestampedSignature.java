package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.payment.PaymentGateway.InvalidWebhookException;
import java.time.Clock;
import java.time.Duration;

/**
 * The "t=timestamp,v1=hex" signature scheme used by Stripe (and by the mock gateway):
 * HMAC-SHA256 over "timestamp.payload". The timestamp stops an attacker from replaying a
 * captured, validly-signed webhook days later.
 */
final class TimestampedSignature {

    static final Duration TOLERANCE = Duration.ofMinutes(5);

    private TimestampedSignature() {}

    static String sign(String secret, long timestampSeconds, String payload) {
        return "t=" + timestampSeconds + ",v1=" + HmacSigner.sha256Hex(secret, timestampSeconds + "." + payload);
    }

    static void verify(String secret, String header, String payload, Clock clock) {
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookException("Missing signature header");
        }
        Long timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    throw new InvalidWebhookException("Bad signature timestamp");
                }
            } else if (kv[0].equals("v1") && signature == null) {
                signature = kv[1];
            }
        }
        if (timestamp == null || signature == null) {
            throw new InvalidWebhookException("Malformed signature header");
        }
        long age = Math.abs(clock.instant().getEpochSecond() - timestamp);
        if (age > TOLERANCE.toSeconds()) {
            throw new InvalidWebhookException("Signature timestamp outside the tolerance window");
        }
        String expected = HmacSigner.sha256Hex(secret, timestamp + "." + payload);
        if (!HmacSigner.matches(expected, signature)) {
            throw new InvalidWebhookException("Signature does not match");
        }
    }
}
