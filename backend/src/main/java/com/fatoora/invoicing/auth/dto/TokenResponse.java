package com.fatoora.invoicing.auth.dto;

/**
 * {@code expiresIn} is in seconds, as in OAuth 2. The refresh token is traded at
 * POST /auth/refresh for a new pair before the access token runs out.
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
        long refreshExpiresIn) {

    public TokenResponse withRefreshToken(String token, long expiresInSeconds) {
        return new TokenResponse(accessToken, tokenType, expiresIn, token, expiresInSeconds);
    }
}
