package com.fatoora.invoicing.tax;

import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A named sales-tax percentage, e.g. "GST 16%". Archived instead of deleted, because products and invoices refer to it. */
@Entity
@Table(name = "tax_rates")
public class TaxRate extends TenantScopedEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 6, scale = 3)
    private BigDecimal rate;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean archived;

    protected TaxRate() {}

    public TaxRate(String name, BigDecimal rate) {
        update(name, rate);
    }

    void update(String name, BigDecimal rate) {
        this.name = name.trim();
        this.rate = rate;
    }

    void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
        if (archived) {
            this.isDefault = false;
        }
    }

    public String getName() {
        return name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isArchived() {
        return archived;
    }
}
