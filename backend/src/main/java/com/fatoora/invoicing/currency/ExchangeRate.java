package com.fatoora.invoicing.currency;

import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A rate the organization entered: 1 {@code currency} = {@code rate} {@code baseCurrency}, from a date. */
@Entity
@Table(name = "exchange_rates")
public class ExchangeRate extends TenantScopedEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private String source;

    protected ExchangeRate() {}

    ExchangeRate(String currency, String baseCurrency, BigDecimal rate, LocalDate effectiveDate) {
        this.currency = currency;
        this.baseCurrency = baseCurrency;
        this.rate = rate;
        this.effectiveDate = effectiveDate;
        this.source = "MANUAL";
    }

    public String getCurrency() {
        return currency;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }
}
