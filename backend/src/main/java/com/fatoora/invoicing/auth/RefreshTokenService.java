package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.organization.MembershipRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long-lived sessions without long-lived access tokens. A refresh token (256 random bits,
 * stored as SHA-256) is traded for a new access token and a new refresh token. Rotation
 * plus reuse detection: each token works once; if a used one comes back after
 * {@link #REUSE_GRACE}, someone copied it, so the whole family is revoked. Within the
 * grace window a repeat is the browser's parallel requests racing, and simply rotates again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public static final Duration LIFETIME = Duration.ofDays(30);
    static final Duration REUSE_GRACE = Duration.ofSeconds(60);

    /** What a valid refresh yields: who, which organization, and the next refresh token. */
    public record Refreshed(UUID userId, UUID organizationId, String refreshToken) {}

    private record Row(UUID id, UUID familyId, UUID userId, UUID organizationId, Instant expiresAt, Instant usedAt,
            Instant revokedAt) {}

    private final JdbcTemplate jdbc;
    private final MembershipRepository memberships;
    private final Clock clock;
    private final java.security.SecureRandom random = new java.security.SecureRandom();

    public RefreshTokenService(JdbcTemplate jdbc, MembershipRepository memberships, Clock clock) {
        this.jdbc = jdbc;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** Starts a new family (sign-in, sign-up, switching organization). */
    @Transactional
    public String start(UUID userId, UUID organizationId) {
        return insert(UuidVersion7Strategy.INSTANCE.generateUuid(null), userId, organizationId);
    }

    /**
     * Trades a refresh token for the next one. Any problem is the same 401. The 401 must
     * not roll back: revoking the family on reuse is the whole point, so it has to stick.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Refreshed rotate(String token) {
        Instant now = clock.instant();
        Row row = find(token);
        if (row == null || row.revokedAt() != null || !now.isBefore(row.expiresAt())) {
            throw invalid();
        }
        if (row.usedAt() != null) {
            if (now.isAfter(row.usedAt().plus(REUSE_GRACE))) {
                revokeFamily(row.familyId(), now);
                log.warn("Refresh token reused after rotation; revoked session family {} of user {}", row.familyId(),
                        row.userId());
                throw invalid();
            }
        } else {
            jdbc.update("UPDATE refresh_tokens SET used_at = ? WHERE id = ?", Timestamp.from(now), row.id());
        }
        // Still a member? (Someone removed from an organization loses access within the hour.)
        if (row.organizationId() != null && !memberships.existsInOrganization(row.organizationId(), row.userId())) {
            revokeFamily(row.familyId(), now);
            throw invalid();
        }
        String next = insert(row.familyId(), row.userId(), row.organizationId());
        return new Refreshed(row.userId(), row.organizationId(), next);
    }

    /** Sign-out: this device's whole chain stops working. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String token) {
        Row row = find(token);
        if (row != null) {
            revokeFamily(row.familyId(), clock.instant());
        }
    }

    private String insert(UUID familyId, UUID userId, UUID organizationId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("""
                INSERT INTO refresh_tokens (id, token_hash, family_id, user_id, organization_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UuidVersion7Strategy.INSTANCE.generateUuid(null), EmailVerificationService.hash(token), familyId, userId,
                organizationId, Timestamp.from(clock.instant().plus(LIFETIME)));
        return token;
    }

    private Row find(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return null;
        }
        return jdbc.query("""
                SELECT id, family_id, user_id, organization_id, expires_at, used_at, revoked_at
                FROM refresh_tokens WHERE token_hash = ? FOR UPDATE
                """, rs -> rs.next() ? new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(),
                        rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant(),
                        rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()) : null,
                EmailVerificationService.hash(token));
    }

    private void revokeFamily(UUID familyId, Instant now) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = ? WHERE family_id = ? AND revoked_at IS NULL",
                Timestamp.from(now), familyId);
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("Your session has ended. Please sign in again.");
    }
}
