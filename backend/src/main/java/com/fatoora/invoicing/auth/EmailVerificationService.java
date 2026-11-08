package com.fatoora.invoicing.auth;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.ratelimit.RateLimiter;
import com.fatoora.invoicing.mail.MailProperties;
import com.fatoora.invoicing.mail.MailService;
import com.fatoora.invoicing.mail.MailService.OutgoingMail;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms that a user owns their email address. The link carries 256 random bits; only
 * their SHA-256 is stored. Links expire after {@link #LIFETIME} and work once.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    static final Duration LIFETIME = Duration.ofDays(3);

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final MailService mail;
    private final MailProperties urls;
    private final RateLimiter limiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository users, JdbcTemplate jdbc, MailService mail, MailProperties urls,
            RateLimiter limiter, Clock clock) {
        this.users = users;
        this.jdbc = jdbc;
        this.mail = mail;
        this.urls = urls;
        this.limiter = limiter;
        this.clock = clock;
    }

    /**
     * Creates a link and emails it. A mail failure is logged, not thrown: sign-up still
     * succeeds and the user can ask for a new link from the banner in the app.
     */
    public void sendLink(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO email_verification_tokens (token_hash, user_id, expires_at) VALUES (?, ?, ?)",
                hash(token), user.getId(), Timestamp.from(clock.instant().plus(LIFETIME)));
        try {
            mail.send(new OutgoingMail(user.getEmail(), null, "Fatoora", "Confirm your email address for Fatoora",
                    "verify-email", Map.of("name", user.getFullName(), "link", urls.publicUrl() + "/verify-email/" + token),
                    null));
        } catch (RuntimeException e) {
            log.warn("Could not send the verification email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    /** The banner's "Resend": at most 3 links per hour per user. */
    @Transactional
    public void resend(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        if (user.isEmailVerified()) {
            throw ApiException.conflict("Your email address is already confirmed");
        }
        limiter.hit("verify-resend", userId.toString(), 3, Duration.ofHours(1),
                "We already sent a few links. Check your inbox and spam folder, or try again later.");
        sendLink(user);
    }

    /** Marks the address confirmed. Unknown, used and expired links all get the same answer. */
    @Transactional
    public void verify(String token) {
        Instant now = clock.instant();
        UUID userId = jdbc.query("""
                UPDATE email_verification_tokens SET used_at = ?
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?
                RETURNING user_id
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                Timestamp.from(now), hash(token == null ? "" : token), Timestamp.from(now));
        if (userId == null) {
            throw ApiException.badRequest("This link is invalid or has expired. Sign in and ask for a new one.");
        }
        users.findById(userId).ifPresent(u -> u.markEmailVerified(now));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
