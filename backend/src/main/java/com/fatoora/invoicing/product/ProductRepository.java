package com.fatoora.invoicing.product;

import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Tenant-scoped automatically through Product's @TenantId. */
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    long countByArchivedFalse();

    boolean existsBySku(String sku);

    static Specification<Product> matching(String query, boolean includeArchived) {
        return (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!includeArchived) {
                predicates.add(cb.isFalse(root.get("archived")));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("sku")), like, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
