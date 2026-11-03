package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Money received against an invoice, by hand (cash, transfer, cheque) or online. */
@Entity
@Table(name = "payments")
public class Payment extends TenantScopedEntity {

    public enum Method {
        CASH,
        BANK_TRANSFER,
        CHEQUE,
        CARD,
        ONLINE,
        OTHER
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Method method;

    @Column(name = "paid_on", nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(updatable = false)
    private String reference;

    @Column(updatable = false)
    private String provider;

    @Column(name = "provider_payment_id", updatable = false)
    private String providerPaymentId;

    @Column(name = "recorded_by", updatable = false)
    private UUID recordedBy;

    protected Payment() {}

    public Payment(Invoice invoice, Money amount, Method method, LocalDate paidOn, String reference, UUID recordedBy,
            String provider, String providerPaymentId) {
        this.invoice = invoice;
        this.amountMinor = amount.amountMinor();
        this.currency = amount.currency();
        this.method = method;
        this.paidOn = paidOn;
        this.reference = reference;
        this.recordedBy = recordedBy;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, currency);
    }

    public Method getMethod() {
        return method;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public String getReference() {
        return reference;
    }

    public String getProvider() {
        return provider;
    }
}
