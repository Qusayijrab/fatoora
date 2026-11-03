package com.fatoora.invoicing.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings from {@code app.jwt.*}. The app refuses to start if the secret is
 * shorter than 32 characters: HS256 needs at least a 256-bit key.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @NotNull Duration ttl) {}
