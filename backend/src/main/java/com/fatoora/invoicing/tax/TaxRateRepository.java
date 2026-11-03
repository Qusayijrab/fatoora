package com.fatoora.invoicing.tax;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically: every query only sees the current organization's rates. */
public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    List<TaxRate> findAllByOrderByArchivedAscNameAsc();

    List<TaxRate> findByArchivedFalseOrderByNameAsc();

    Optional<TaxRate> findByIsDefaultTrue();

    boolean existsByNameIgnoreCase(String name);
}
