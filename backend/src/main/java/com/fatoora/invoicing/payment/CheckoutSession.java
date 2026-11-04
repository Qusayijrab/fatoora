package com.fatoora.invoicing.payment;

import com.fatoora.invoicing.common.BaseEntity;
import com.fatoora.invoicing.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A checkout we started with the provider. Webhooks arrive with no logged-in user, so
 * this row is looked up by the provider's session id, and *its* organization id (from
 * our own database, not from the webhook) decides which tenant the payment belongs to.
 */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession extends BaseEntity {

    public enum Status {
        OPEN,
        COMPLETED,
        EXPIRED
    }

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_session_id", nullable = false, updatable = false)
    private String providerSessionId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "checkout_url", nullable = false, columnDefinition = "text")
    private String checkoutUrl;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected CheckoutSession() {}

    CheckoutSession(UUID organizationId, UUID invoiceId, String provider, String providerSessionId, Money amount, String url,
            Instant expiresAt) {
        this.organizationId = organizationId;
        this.invoiceId = invoiceId;
        this.provider = provider;
        this.providerSessionId = providerSessionId;
        this.amountMinor = amount.amountMinor();
        this.currency = amount.currency();
        this.status = Status.OPEN;
        this.checkoutUrl = url;
        this.expiresAt = expiresAt;
    }

    void complete(Instant now) {
        this.status = Status.COMPLETED;
        this.completedAt = now;
    }

    void expire() {
        if (status == Status.OPEN) {
            this.status = Status.EXPIRED;
        }
    }

    /** Open and not past its expiry: the payer can still complete it. */
    boolean isPayable(Instant now) {
        return status == Status.OPEN && (expiresAt == null || now.isBefore(expiresAt));
    }

    /** OPEN in the database but past its expiry counts as expired. */
    Status effectiveStatus(Instant now) {
        return status == Status.OPEN && !isPayable(now) ? Status.EXPIRED : status;
    }

    public String getProvider() {
        return provider;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, currency);
    }

    public Status getStatus() {
        return status;
    }
}
