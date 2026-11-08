package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.ratelimit.RateLimiter;
import com.fatoora.invoicing.common.ratelimit.RateLimits;
import com.fatoora.invoicing.mail.MailProperties;
import com.fatoora.invoicing.mail.MailService;
import com.fatoora.invoicing.mail.MailService.OutgoingMail;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password". Asking for a link always gets the same answer, whether or not the
 * address has an account, so it can't be used to find out who uses Fatoora. The link
 * carries 256 random bits (only the SHA-256 is stored), lasts {@link #LIFETIME} and works
 * once. Setting the new password signs out every session and kills any other link.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    static final Duration LIFETIME = Duration.ofHours(1);

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final MailProperties urls;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final RateLimiter limiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, JdbcTemplate jdbc, MailService mail, MailProperties urls,
            PasswordEncoder passwords, RefreshTokenService refreshTokens, RateLimiter limiter, Clock clock) {
        this.users = users;
        this.jdbc = jdbc;
        this.mail = mail;
        this.urls = urls;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.limiter = limiter;
        this.clock = clock;
    }

    /** Emails a reset link if the address has an account. Same result either way. */
    @Transactional
    public void requestLink(String email, String clientIp) {
        limiter.hit("reset-ip", clientIp, 10, Duration.ofHours(1), "Too many requests. Try again later.");
        String normalized = RateLimits.emailKey(email);
        // Per address too, quietly: an attacker can't flood someone's inbox, and the answer stays the same.
        try {
            limiter.hit("reset-email", normalized, 3, Duration.ofHours(1), "quiet");
        } catch (RateLimiter.LimitExceeded e) {
            return;
        }
        users.findByEmail(User.normalizeEmail(normalized)).ifPresent(user -> {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("INSERT INTO password_reset_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                    EmailVerificationService.hash(token), user.getId(), Timestamp.from(clock.instant().plus(LIFETIME)));
            try {
                mail.send(new OutgoingMail(user.getEmail(), null, "Fatoora", "Reset your Fatoora password", "reset-password",
                        Map.of("name", user.getFullName(), "link", urls.publicUrl() + "/reset-password/" + token), null));
            } catch (RuntimeException e) {
                log.warn("Could not send a password reset email to user {}: {}", user.getId(), e.getMessage());
            }
        });
    }

    /**
     * Sets the new password from a valid link. Every session of the account is signed out
     * and every other reset link stops working. Reaching the inbox also confirms the address.
     */
    @Transactional
    public void reset(String token, String newPassword) {
        Instant now = clock.instant();
        UUID userId = jdbc.query("""
                UPDATE password_reset_tokens SET used_at = ?
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                RETURNING user_id
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                Timestamp.from(now), EmailVerificationService.hash(token == null ? "" : token), Timestamp.from(now));
        if (userId == null) {
            throw ApiException.badRequest("This link is invalid or has expired. Ask for a new one.");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.badRequest("This link is invalid or has expired."));
        user.changePassword(passwords.encode(newPassword));
        user.markEmailVerified(now);
        jdbc.update("UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                Timestamp.from(now), userId);
        refreshTokens.revokeAllForUser(userId);
        log.info("Password reset for user {}; all sessions signed out", userId);
    }
}
