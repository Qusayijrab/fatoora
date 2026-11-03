package com.fatoora.invoicing.currency;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically through @TenantId. */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /** The rate in force on {@code date}: the latest one that started on or before it. */
    Optional<ExchangeRate> findFirstByCurrencyAndBaseCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDescCreatedAtDesc(
            String currency, String baseCurrency, LocalDate date);

    List<ExchangeRate> findAllByOrderByCurrencyAscEffectiveDateDesc();
}
