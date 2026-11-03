package com.fatoora.invoicing.publiclink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A capability URL: whoever holds the token may see one thing (an invoice's payment page,
 * or a client's portal). Deliberately not tenant-scoped: it is how a request with no
 * login finds its organization. Everything after the lookup runs inside that organization.
 */
@Entity
@Table(name = "public_links")
public class PublicLink implements Persistable<String> {

    public enum Kind {
        INVOICE,
        CLIENT_PORTAL
    }

    @Id
    private String token;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @jakarta.persistence.Transient
    private boolean isNew;

    protected PublicLink() {}

    PublicLink(String token, UUID organizationId, Kind kind, UUID targetId, Instant now) {
        this.token = token;
        this.organizationId = organizationId;
        this.kind = kind;
        this.targetId = targetId;
        this.createdAt = now;
        this.isNew = true;
    }

    void revoke(Instant now) {
        this.revokedAt = now;
    }

    @Override
    public String getId() {
        return token;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getToken() {
        return token;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
