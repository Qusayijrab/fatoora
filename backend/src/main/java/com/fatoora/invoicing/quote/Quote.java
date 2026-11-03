package com.fatoora.invoicing.quote;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import com.fatoora.invoicing.document.LineCalculator;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import com.fatoora.invoicing.document.LineCalculator.Totals;
import com.fatoora.invoicing.document.Party;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A price proposal. Editable while DRAFT; numbered and frozen when sent. */
@Entity
@Table(name = "quotes")
public class Quote extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(columnDefinition = "text")
    private String terms;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "bill_to_name")),
        @AttributeOverride(name = "email", column = @Column(name = "bill_to_email", columnDefinition = "citext")),
        @AttributeOverride(name = "phone", column = @Column(name = "bill_to_phone")),
        @AttributeOverride(name = "taxId", column = @Column(name = "bill_to_tax_id")),
        @AttributeOverride(name = "addressLine1", column = @Column(name = "bill_to_address_line1")),
        @AttributeOverride(name = "addressLine2", column = @Column(name = "bill_to_address_line2")),
        @AttributeOverride(name = "city", column = @Column(name = "bill_to_city")),
        @AttributeOverride(name = "region", column = @Column(name = "bill_to_region")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "bill_to_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "bill_to_country", length = 2)),
    })
    private Party billTo;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "converted_invoice_id")
    private UUID convertedInvoiceId;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<QuoteLine> lines = new ArrayList<>();

    protected Quote() {}

    public static Quote draft(Client client, String currency, LocalDate issueDate) {
        Quote q = new Quote();
        q.client = client;
        q.currency = currency;
        q.issueDate = issueDate;
        q.status = QuoteStatus.DRAFT;
        return q;
    }

    public void edit(Client client, String currency, LocalDate issueDate, LocalDate validUntil, String notes,
            String terms, List<CalculatedLine> newLines) {
        if (status != QuoteStatus.DRAFT) {
            throw ApiException.conflict("A sent quote cannot be edited. Create a new quote instead.");
        }
        if (validUntil != null && validUntil.isBefore(issueDate)) {
            throw ApiException.badRequest("\"Valid until\" cannot be before the quote date");
        }
        this.client = client;
        this.currency = currency;
        this.issueDate = issueDate;
        this.validUntil = validUntil;
        this.notes = notes;
        this.terms = terms;
        lines.clear();
        for (int i = 0; i < newLines.size(); i++) {
            lines.add(new QuoteLine(this, i + 1, newLines.get(i)));
        }
        Totals totals = LineCalculator.totals(newLines, currency);
        this.subtotalMinor = totals.subtotal().amountMinor();
        this.taxMinor = totals.tax().amountMinor();
        this.totalMinor = totals.total().amountMinor();
    }

    public void requireSendable() {
        if (!status.canMoveTo(QuoteStatus.SENT)) {
            throw ApiException.conflict("Only draft quotes can be sent");
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Add at least one line before sending");
        }
    }

    public void send(String number, Instant now) {
        requireSendable();
        moveTo(QuoteStatus.SENT);
        this.number = number;
        this.billTo = Party.of(client);
        this.sentAt = now;
    }

    public void accept(Instant now) {
        moveTo(QuoteStatus.ACCEPTED);
        this.acceptedAt = now;
    }

    public void reject(Instant now) {
        moveTo(QuoteStatus.REJECTED);
        this.rejectedAt = now;
    }

    /** True if it was SENT and is now past its validity date (organization time zone). */
    public boolean expireIfLate(LocalDate today) {
        if (status == QuoteStatus.SENT && validUntil != null && validUntil.isBefore(today)) {
            moveTo(QuoteStatus.EXPIRED);
            return true;
        }
        return false;
    }

    /** The lines to copy into an invoice draft; refuses a second conversion. */
    public List<CalculatedLine> linesForConversion() {
        if (status != QuoteStatus.ACCEPTED) {
            throw ApiException.conflict("Only accepted quotes can be turned into an invoice");
        }
        if (convertedInvoiceId != null) {
            throw ApiException.conflict("This quote has already been turned into an invoice");
        }
        return lines.stream()
                .map(l -> LineCalculator.calculate(new LineDraft(l.getProductId(), l.getDescription(), l.getQuantity(),
                        l.getUnit(), l.unitPrice(currency), l.getTaxRateId(), l.getTaxRate())))
                .toList();
    }

    public void markConverted(UUID invoiceId) {
        this.convertedInvoiceId = invoiceId;
    }

    private void moveTo(QuoteStatus target) {
        if (!status.canMoveTo(target)) {
            throw ApiException.conflict("A quote cannot go from " + status.name().toLowerCase() + " to "
                    + target.name().toLowerCase());
        }
        this.status = target;
    }

    public String displayClientName() {
        return billTo != null && billTo.getName() != null ? billTo.getName() : client.getName();
    }

    public Client getClient() {
        return client;
    }

    public String getNumber() {
        return number;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getNotes() {
        return notes;
    }

    public String getTerms() {
        return terms;
    }

    public Money getSubtotal() {
        return Money.ofMinor(subtotalMinor, currency);
    }

    public Money getTax() {
        return Money.ofMinor(taxMinor, currency);
    }

    public Money getTotal() {
        return Money.ofMinor(totalMinor, currency);
    }

    public Party getBillTo() {
        return billTo;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public UUID getConvertedInvoiceId() {
        return convertedInvoiceId;
    }

    public List<QuoteLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
