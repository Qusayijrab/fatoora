package com.fatoora.invoicing.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<AuditEvent> findByOrganizationIdAndEntityTypeOrderByCreatedAtDesc(
            UUID organizationId, String entityType, Pageable pageable);

    List<AuditEvent> findByOrganizationIdAndEntityIdOrderByCreatedAtAsc(UUID organizationId, UUID entityId);
}
