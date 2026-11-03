package com.fatoora.invoicing.document;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Columns shared by quote lines and invoice lines. A line copies the unit price and
 * the tax *rate value* at the time it is written, and stores its computed amounts,
 * so a document's numbers never move after the fact.
 */
@MappedSuperclass
public abstract class LineFields extends TenantScopedEntity {

    @Column(nullable = false)
    private int position;

    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    private String unit;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "tax_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal taxRate;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    protected LineFields() {}

    protected void fill(int position, LineCalculator.CalculatedLine line) {
        this.position = position;
        this.productId = line.productId();
        this.description = line.description();
        this.quantity = line.quantity();
        this.unit = line.unit();
        this.unitPriceMinor = line.unitPrice().amountMinor();
        this.taxRateId = line.taxRateId();
        this.taxRate = line.taxRate();
        this.subtotalMinor = line.subtotal().amountMinor();
        this.taxMinor = line.tax().amountMinor();
        this.totalMinor = line.total().amountMinor();
    }

    public int getPosition() {
        return position;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public UUID getTaxRateId() {
        return taxRateId;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public Money unitPrice(String currency) {
        return Money.ofMinor(unitPriceMinor, currency);
    }

    public Money subtotal(String currency) {
        return Money.ofMinor(subtotalMinor, currency);
    }

    public Money tax(String currency) {
        return Money.ofMinor(taxMinor, currency);
    }

    public Money total(String currency) {
        return Money.ofMinor(totalMinor, currency);
    }
}
