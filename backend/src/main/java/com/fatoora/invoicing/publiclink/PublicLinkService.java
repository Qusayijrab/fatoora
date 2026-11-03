package com.fatoora.invoicing.publiclink;

import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.mail.MailProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and resolves public links. Tokens are 32 bytes from SecureRandom (256 bits),
 * base64url-encoded: not guessable, not enumerable, and unrelated to any database id.
 */
@Service
public class PublicLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PublicLinkRepository links;
    private final MailProperties mail;
    private final Clock clock;

    public PublicLinkService(PublicLinkRepository links, MailProperties mail, Clock clock) {
        this.links = links;
        this.mail = mail;
        this.clock = clock;
    }

    /** The active link for a target in the current organization, created on first use. */
    @Transactional(propagation = Propagation.REQUIRED)
    public PublicLink getOrCreate(PublicLink.Kind kind, UUID targetId) {
        UUID orgId = TenantContext.require().organizationId();
        return links.findByOrganizationIdAndKindAndTargetIdAndRevokedAtIsNull(orgId, kind, targetId)
                .orElseGet(() -> links.save(new PublicLink(newToken(), orgId, kind, targetId, clock.instant())));
    }

    /** Resolves a token from a public URL; unknown, revoked or wrong-kind tokens are all "not found". */
    @Transactional(readOnly = true)
    public PublicLink resolve(String token, PublicLink.Kind expected) {
        if (token == null || token.length() < 32 || token.length() > 64) {
            throw notFound();
        }
        PublicLink link = links.findById(token).orElseThrow(PublicLinkService::notFound);
        if (link.isRevoked() || link.getKind() != expected) {
            throw notFound();
        }
        return link;
    }

    @Transactional
    public void revoke(PublicLink.Kind kind, UUID targetId) {
        UUID orgId = TenantContext.require().organizationId();
        links.findByOrganizationIdAndKindAndTargetIdAndRevokedAtIsNull(orgId, kind, targetId)
                .ifPresent(link -> link.revoke(clock.instant()));
    }

    public String payUrl(PublicLink link) {
        return mail.publicUrl() + "/pay/" + link.getToken();
    }

    public String portalUrl(PublicLink link) {
        return mail.publicUrl() + "/portal/" + link.getToken();
    }

    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ApiException notFound() {
        return ApiException.notFound("This link is not valid. Ask the sender for a new one.");
    }
}
