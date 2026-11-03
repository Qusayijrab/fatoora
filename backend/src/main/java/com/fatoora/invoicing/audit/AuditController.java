package com.fatoora.invoicing.audit;

import com.fatoora.invoicing.common.PageResponse;
import com.fatoora.invoicing.common.tenancy.CurrentTenant;
import com.fatoora.invoicing.common.tenancy.TenantContext;
import com.fatoora.invoicing.organization.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the audit trail. The history of one document (entityId) is visible to every
 * member; the organization-wide log is for owners and admins.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    public record AuditView(UUID id, String entityType, UUID entityId, String action, String summary, String actorName,
            String data, Instant at) {}

    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AuditView> list(
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        CurrentTenant tenant = TenantContext.require();
        if (entityId == null) {
            tenant.requireRole(Role.ADMIN);
        }
        int limit = Math.clamp(size, 1, 200);
        int offset = Math.max(page, 0) * limit;
        String where = "e.organization_id = ?" + (entityId != null ? " AND e.entity_id = ?" : "")
                + (entityType != null ? " AND e.entity_type = ?" : "");
        List<Object> args = new java.util.ArrayList<>(List.of(tenant.organizationId()));
        if (entityId != null) {
            args.add(entityId);
        }
        if (entityType != null) {
            args.add(entityType);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events e WHERE " + where, Long.class, args.toArray());
        List<Object> pageArgs = new java.util.ArrayList<>(args);
        pageArgs.add(limit);
        pageArgs.add(offset);
        List<AuditView> items = jdbc.query("""
                SELECT e.id, e.entity_type, e.entity_id, e.action, e.summary, u.full_name, e.data::text, e.created_at
                FROM audit_events e LEFT JOIN users u ON u.id = e.actor_user_id
                WHERE %s ORDER BY e.created_at DESC LIMIT ? OFFSET ?
                """.formatted(where), (rs, n) -> new AuditView(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getTimestamp(8).toInstant()), pageArgs.toArray());
        return new PageResponse<>(items, Math.max(page, 0), limit, total == null ? 0 : total);
    }
}
