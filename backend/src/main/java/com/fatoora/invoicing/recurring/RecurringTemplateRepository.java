package com.fatoora.invoicing.recurring;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped automatically through @TenantId. */
public interface RecurringTemplateRepository extends JpaRepository<RecurringTemplate, UUID> {

    @EntityGraph(attributePaths = {"client"})
    List<RecurringTemplate> findAllByOrderByStatusAscNextRunDateAsc();

    @EntityGraph(attributePaths = {"lines", "client"})
    Optional<RecurringTemplate> findWithLinesById(UUID id);

    /** Row lock: two job instances (or a job and "run now") can't generate the same period twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RecurringTemplate t where t.id = :id")
    Optional<RecurringTemplate> findForUpdate(@Param("id") UUID id);
}
