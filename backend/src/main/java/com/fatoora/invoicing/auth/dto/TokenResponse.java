package com.fatoora.invoicing.auth.dto;

/** {@code expiresIn} is in seconds, as in OAuth 2. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
