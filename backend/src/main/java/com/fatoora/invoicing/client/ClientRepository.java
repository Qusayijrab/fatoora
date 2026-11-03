package com.fatoora.invoicing.client;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Tenant-scoped automatically through Client's @TenantId. */
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    long countByArchivedFalse();

    static Specification<Client> matching(String query, boolean includeArchived) {
        return (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!includeArchived) {
                predicates.add(cb.isFalse(root.get("archived")));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(java.util.Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("taxId")), like, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
