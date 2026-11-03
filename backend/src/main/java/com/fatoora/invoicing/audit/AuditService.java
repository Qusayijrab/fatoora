package com.fatoora.invoicing.audit;

import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records who did what. Joins the caller's transaction (MANDATORY), so an audit line
 * is written if and only if the change it describes is committed.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final JsonMapper json;
    private final Clock clock;

    public AuditService(AuditEventRepository events, JsonMapper json, Clock clock) {
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    /** Records an event in the current organization, by the current user. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String entityType, UUID entityId, String action, String summary, Map<String, ?> data) {
        CurrentTenant tenant = TenantContext.require();
        record(tenant.organizationId(), tenant.userId(), entityType, entityId, action, summary, data);
    }

    /** Explicit variant for flows with no active tenant yet (sign-up) or system jobs (actor = null). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID organizationId, UUID actorUserId, String entityType, UUID entityId, String action,
            String summary, Map<String, ?> data) {
        events.save(new AuditEvent(
                UuidVersion7Strategy.INSTANCE.generateUuid(null),
                organizationId,
                actorUserId,
                entityType,
                entityId,
                action,
                summary,
                json.writeValueAsString(data == null ? Map.of() : data),
                clock.instant()));
    }
}
