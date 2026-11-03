package com.fatoora.invoicing.quote;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped automatically through Quote's @TenantId. */
public interface QuoteRepository extends JpaRepository<Quote, UUID>, JpaSpecificationExecutor<Quote> {

    @EntityGraph(attributePaths = {"lines", "client"})
    Optional<Quote> findWithLinesById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> findForUpdate(@Param("id") UUID id);

    long countByNumberIsNotNull();

    static Specification<Quote> matching(QuoteStatus status, UUID clientId, String query) {
        return (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (clientId != null) {
                p.add(cb.equal(root.get("client").get("id"), clientId));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                var client = root.join("client");
                p.add(cb.or(
                        cb.like(cb.lower(root.get("number")), like, '\\'),
                        cb.like(cb.lower(client.get("name")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
