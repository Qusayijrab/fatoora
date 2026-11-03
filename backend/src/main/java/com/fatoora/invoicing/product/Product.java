package com.fatoora.invoicing.product;

import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import com.fatoora.invoicing.tax.TaxRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Something the organization sells, with a default price. Lines on quotes and invoices
 * copy the price at the time, so changing it here never changes an existing document.
 */
@Entity
@Table(name = "products")
public class Product extends TenantScopedEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private String sku;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rate_id")
    private TaxRate taxRate;

    @Column(nullable = false)
    private boolean archived;

    protected Product() {}

    public Product(String name, String description, String sku, String unit, Money unitPrice, TaxRate taxRate) {
        update(name, description, sku, unit, unitPrice, taxRate);
    }

    void update(String name, String description, String sku, String unit, Money unitPrice, TaxRate taxRate) {
        this.name = name.trim();
        this.description = Strings.clean(description);
        this.sku = Strings.clean(sku);
        this.unit = Strings.clean(unit) == null ? "item" : unit.trim();
        this.unitPriceMinor = unitPrice.amountMinor();
        this.currency = unitPrice.currency();
        this.taxRate = taxRate;
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSku() {
        return sku;
    }

    public String getUnit() {
        return unit;
    }

    public Money getUnitPrice() {
        return Money.ofMinor(unitPriceMinor, currency);
    }

    public TaxRate getTaxRate() {
        return taxRate;
    }

    public boolean isArchived() {
        return archived;
    }
}
