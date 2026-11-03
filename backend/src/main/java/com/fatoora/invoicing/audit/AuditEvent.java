package com.fatoora.invoicing.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * One line in the append-only audit trail. {@code @Immutable}: Hibernate never issues an
 * UPDATE, and a database trigger rejects UPDATE/DELETE as a second guard.
 *
 * <p>Not a TenantScopedEntity on purpose: events are also written while an organization
 * is being created (before any tenant is active). Reads are scoped explicitly by org id.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String summary;

    @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    AuditEvent(UUID id, UUID organizationId, UUID actorUserId, String entityType, UUID entityId, String action,
            String summary, String data, Instant createdAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.actorUserId = actorUserId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.summary = summary;
        this.data = data;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    /** Always new: events are only ever inserted, so save() must never try a merge. */
    @Override
    public boolean isNew() {
        return true;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getSummary() {
        return summary;
    }

    public String getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
