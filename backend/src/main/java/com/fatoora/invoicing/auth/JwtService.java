package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.auth.dto.TokenResponse;
import com.fatoora.invoicing.common.tenancy.TenantFilter;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens. Claims:
 * <ul>
 *   <li>{@code sub} - user id</li>
 *   <li>{@code org} - active organization id (absent if the user has no org)</li>
 *   <li>{@code iss}, {@code iat}, {@code exp} - standard issuer / issued-at / expiry</li>
 * </ul>
 * The token is signed with the server's secret, so a client can't edit the org claim.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issue(UUID userId, @Nullable UUID organizationId) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()));
        if (organizationId != null) {
            claims.claim(TenantFilter.ORG_CLAIM, organizationId.toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttl().toSeconds(), null, 0);
    }
}
