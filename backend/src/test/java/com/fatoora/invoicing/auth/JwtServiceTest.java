package com.fatoora.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

    private static final JwtProperties PROPS =
            new JwtProperties("fatoora", "unit-test-secret-unit-test-secret-0123", Duration.ofHours(1));

    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = config.jwtSigningKey(PROPS);
    private final JwtDecoder decoder = config.jwtDecoder(key, PROPS);

    private JwtService serviceAt(Instant now) {
        return new JwtService(config.jwtEncoder(key), PROPS, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenCarriesUserOrgIssuerAndExpiry() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant now = Instant.now();

        var response = serviceAt(now).issue(userId, orgId);
        Jwt jwt = decoder.decode(response.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("org")).isEqualTo(orgId.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("fatoora");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(1)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    void orgClaimIsOmittedWhenUserHasNoOrganization() {
        Jwt jwt = decoder.decode(serviceAt(Instant.now()).issue(UUID.randomUUID(), null).accessToken());
        assertThat(jwt.hasClaim("org")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(Instant.now().minus(Duration.ofHours(2))).issue(UUID.randomUUID(), null).accessToken();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithEditedOrgClaimIsRejected() {
        String token = serviceAt(Instant.now()).issue(UUID.randomUUID(), UUID.randomUUID()).accessToken();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replaceAll("\"org\":\"[^\"]+\"", "\"org\":\"" + UUID.randomUUID() + "\"");
        String forged = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        var otherProps = new JwtProperties("fatoora", "another-secret-another-secret-another-01", Duration.ofHours(1));
        var otherKey = config.jwtSigningKey(otherProps);
        String token = new JwtService(config.jwtEncoder(otherKey), otherProps, Clock.systemUTC())
                .issue(UUID.randomUUID(), null)
                .accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        var otherProps = new JwtProperties("someone-else", PROPS.secret(), Duration.ofHours(1));
        String token = new JwtService(config.jwtEncoder(key), otherProps, Clock.systemUTC())
                .issue(UUID.randomUUID(), null)
                .accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
