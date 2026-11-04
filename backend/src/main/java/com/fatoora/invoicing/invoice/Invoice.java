package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import com.fatoora.invoicing.document.LineCalculator;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An invoice or credit note. All state changes go through methods on this class, which
 * enforce the lifecycle in {@link InvoiceStatus} and the immutability rule: once issued,
 * the content (client, lines, amounts, dates) can never change again (CLAUDE.md rule 4).
 */
@Entity
@Table(name = "invoices")
public class Invoice extends TenantScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceKind kind;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

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

    @Column(name = "amount_paid_minor", nullable = false)
    private long amountPaidMinor;

    @Column(name = "amount_credited_minor", nullable = false)
    private long amountCreditedMinor;

    /** Money given back to the client after a credit note on a paid invoice. */
    @Column(name = "amount_refunded_minor", nullable = false)
    private long amountRefundedMinor;

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

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "name", column = @Column(name = "seller_name")),
        @AttributeOverride(name = "email", column = @Column(name = "seller_email", columnDefinition = "citext")),
        @AttributeOverride(name = "phone", column = @Column(name = "seller_phone")),
        @AttributeOverride(name = "taxId", column = @Column(name = "seller_tax_id")),
        @AttributeOverride(name = "addressLine1", column = @Column(name = "seller_address_line1")),
        @AttributeOverride(name = "addressLine2", column = @Column(name = "seller_address_line2")),
        @AttributeOverride(name = "city", column = @Column(name = "seller_city")),
        @AttributeOverride(name = "region", column = @Column(name = "seller_region")),
        @AttributeOverride(name = "postalCode", column = @Column(name = "seller_postal_code")),
        @AttributeOverride(name = "country", column = @Column(name = "seller_country", length = 2)),
    })
    private Party seller;

    @Column(name = "quote_id")
    private UUID quoteId;

    // ---- exchange rate frozen at issue (CLAUDE.md rule 6) ----

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    @Column(name = "exchange_rate", precision = 20, scale = 10)
    private java.math.BigDecimal exchangeRate;

    @Column(name = "total_base_minor")
    private Long totalBaseMinor;

    @Column(name = "rate_source")
    private String rateSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credited_invoice_id")
    private Invoice creditedInvoice;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "void_reason", columnDefinition = "text")
    private String voidReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    protected Invoice() {}

    public static Invoice draft(Client client, String currency, LocalDate issueDate, LocalDate dueDate) {
        Invoice invoice = new Invoice();
        invoice.kind = InvoiceKind.INVOICE;
        invoice.status = InvoiceStatus.DRAFT;
        invoice.client = client;
        invoice.currency = currency;
        invoice.issueDate = issueDate;
        invoice.dueDate = dueDate;
        return invoice;
    }

    /**
     * A credit note against an issued invoice, open or paid: same client and currency,
     * issued immediately by the service. On a paid invoice the credit becomes money owed
     * back to the client ({@link #refundDue()}).
     */
    public static Invoice creditNoteFor(Invoice original, LocalDate issueDate, String reason) {
        if (original.kind != InvoiceKind.INVOICE || !original.canBeCredited()) {
            throw ApiException.conflict("Credit notes can only be raised against an issued invoice that is not void");
        }
        Invoice cn = draft(original.client, original.currency, issueDate, null);
        cn.kind = InvoiceKind.CREDIT_NOTE;
        cn.creditedInvoice = original;
        cn.notes = reason;
        return cn;
    }

    // ---- editing (drafts only) ----

    public void edit(Client client, String currency, LocalDate issueDate, LocalDate dueDate, String notes,
            String terms, List<CalculatedLine> newLines) {
        requireDraft();
        if (dueDate != null && dueDate.isBefore(issueDate)) {
            throw ApiException.badRequest("The due date cannot be before the issue date");
        }
        this.client = client;
        this.currency = currency;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.notes = notes;
        this.terms = terms;
        replaceLines(newLines);
    }

    void linkQuote(UUID quoteId) {
        this.quoteId = quoteId;
    }

    private void replaceLines(List<CalculatedLine> newLines) {
        lines.clear();
        for (int i = 0; i < newLines.size(); i++) {
            CalculatedLine line = newLines.get(i);
            if (!line.unitPrice().currency().equals(currency)) {
                throw new IllegalStateException("Line currency differs from document currency");
            }
            lines.add(new InvoiceLine(this, i + 1, line));
        }
        Totals totals = LineCalculator.totals(newLines, currency);
        this.subtotalMinor = totals.subtotal().amountMinor();
        this.taxMinor = totals.tax().amountMinor();
        this.totalMinor = totals.total().amountMinor();
    }

    // ---- lifecycle ----

    /**
     * Dates a draft for issue on {@code today}, keeping its payment terms: a draft written
     * last month goes out dated today, not backdated and already overdue, and a December
     * draft issued in January is numbered in January's year. With {@code keepDates} the
     * draft's own past date stands, as an explicit choice (e.g. entering invoices already
     * given on paper); it is then numbered in that date's year. Never in the future.
     */
    public void dateForIssue(LocalDate today, boolean keepDates) {
        requireDraft();
        if (keepDates) {
            if (issueDate.isAfter(today)) {
                throw ApiException.badRequest("The issue date cannot be in the future");
            }
            return;
        }
        if (!issueDate.equals(today)) {
            if (dueDate != null) {
                this.dueDate = today.plusDays(ChronoUnit.DAYS.between(issueDate, dueDate));
            }
            this.issueDate = today;
        }
    }

    /** Throws unless this draft can be issued. Checked before a number is taken. */
    public void requireIssuable() {
        if (!status.canMoveTo(InvoiceStatus.ISSUED)) {
            throw ApiException.conflict("Only drafts can be issued (this one is " + status.name().toLowerCase().replace('_', ' ') + ")");
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Add at least one line before issuing");
        }
        if (totalMinor <= 0) {
            throw ApiException.badRequest("The total must be more than zero");
        }
    }

    /**
     * Assigns the number and freezes buyer and seller details and the exchange rate:
     * 1 {@code currency} = {@code rate} {@code baseCurrency}. The base total is computed
     * once, here, and never recomputed.
     */
    public void issue(String number, Party seller, Instant now, String baseCurrency, java.math.BigDecimal rate, String rateSource) {
        requireIssuable();
        moveTo(InvoiceStatus.ISSUED);
        this.baseCurrency = baseCurrency;
        this.exchangeRate = rate;
        this.rateSource = rateSource;
        this.totalBaseMinor = getTotal().convert(baseCurrency, rate).amountMinor();
        this.number = number;
        this.billTo = Party.of(client);
        this.seller = seller;
        this.issuedAt = now;
    }

    /** Records money received; the status follows the remaining balance. */
    public void recordPayment(Money amount) {
        requireKind(InvoiceKind.INVOICE);
        settle(amount, "payment");
        this.amountPaidMinor += amount.amountMinor();
        afterSettlement();
    }

    /**
     * Applies an issued credit note. It may be more than the balance due (the client
     * already paid): the excess is owed back as a refund. It can never credit more than
     * was invoiced.
     */
    void applyCredit(Money amount) {
        if (!canBeCredited()) {
            throw ApiException.conflict("A credit note can only be applied to an issued invoice that is not void");
        }
        requireAmount(amount);
        Money creditable = Money.ofMinor(totalMinor - amountCreditedMinor, currency);
        if (amount.compareTo(creditable) > 0) {
            throw ApiException.badRequest("The credit note (" + amount + ") is more than what is left to credit on this "
                    + "invoice (" + creditable + ")");
        }
        this.amountCreditedMinor += amount.amountMinor();
        afterSettlement();
    }

    /** Records money given back to the client; at most what credit notes left owed. */
    public void recordRefund(Money amount) {
        requireKind(InvoiceKind.INVOICE);
        requireAmount(amount);
        if (amount.compareTo(refundDue()) > 0) {
            throw ApiException.badRequest(refundDue().isPositive()
                    ? "The refund (" + amount + ") is more than the amount owed to the client (" + refundDue() + ")"
                    : "Nothing is owed to the client on this invoice. Issue a credit note first.");
        }
        this.amountRefundedMinor += amount.amountMinor();
    }

    private boolean canBeCredited() {
        return status.isOpen() || status == InvoiceStatus.PAID;
    }

    private void settle(Money amount, String what) {
        if (!status.isOpen()) {
            throw ApiException.conflict("A " + what + " can only be recorded on an issued, unpaid invoice (this one is "
                    + status.name().toLowerCase().replace('_', ' ') + ")");
        }
        requireAmount(amount);
        if (amount.compareTo(balanceDue()) > 0) {
            throw ApiException.badRequest("The " + what + " (" + amount + ") is more than the balance due ("
                    + balanceDue() + ")");
        }
    }

    private void requireAmount(Money amount) {
        if (!amount.currency().equals(currency)) {
            throw ApiException.badRequest("This invoice is in " + currency + ", not " + amount.currency());
        }
        if (!amount.isPositive()) {
            throw ApiException.badRequest("The amount must be more than zero");
        }
    }

    private void afterSettlement() {
        if (status == InvoiceStatus.PAID) {
            return; // a credit note on a paid invoice: it stays settled, the excess is a refund due
        }
        if (balanceDue().isZero()) {
            moveTo(InvoiceStatus.PAID);
        } else if (status == InvoiceStatus.ISSUED) {
            moveTo(InvoiceStatus.PARTIALLY_PAID);
        }
        // PARTIALLY_PAID and OVERDUE keep their status until fully settled.
    }

    /** Called by the daily job (Phase 6) and on read: past due in the organization's time zone. */
    public boolean markOverdueIfLate(LocalDate today) {
        if (kind == InvoiceKind.INVOICE
                && (status == InvoiceStatus.ISSUED || status == InvoiceStatus.PARTIALLY_PAID)
                && dueDate != null
                && dueDate.isBefore(today)) {
            moveTo(InvoiceStatus.OVERDUE);
            return true;
        }
        return false;
    }

    /** Cancels the invoice. It keeps its number and stays visible; nothing is deleted. */
    public void voidInvoice(String reason, Instant now) {
        requireKind(InvoiceKind.INVOICE);
        if (status == InvoiceStatus.PAID) {
            throw ApiException.conflict("A paid invoice cannot be voided; issue a credit note instead");
        }
        moveTo(InvoiceStatus.VOID);
        this.voidReason = reason;
        this.voidedAt = now;
    }

    private void moveTo(InvoiceStatus target) {
        if (!status.canMoveTo(target)) {
            throw ApiException.conflict("An invoice cannot go from " + status.name().toLowerCase().replace('_', ' ')
                    + " to " + target.name().toLowerCase().replace('_', ' '));
        }
        this.status = target;
    }

    private void requireDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw ApiException.conflict("Issued invoices cannot be edited. Void it or issue a credit note instead.");
        }
    }

    private void requireKind(InvoiceKind expected) {
        if (kind != expected) {
            throw ApiException.conflict("Not allowed on a " + kind.name().toLowerCase().replace('_', ' '));
        }
    }

    // ---- reads ----

    /** What the client still owes; never negative. */
    public Money balanceDue() {
        return Money.ofMinor(Math.max(0, net()), currency);
    }

    /** What is owed back to the client: paid plus credited beyond the total, less refunds. */
    public Money refundDue() {
        return Money.ofMinor(Math.max(0, -net()), currency);
    }

    private long net() {
        return totalMinor - amountPaidMinor - amountCreditedMinor + amountRefundedMinor;
    }

    public InvoiceKind getKind() {
        return kind;
    }

    public Client getClient() {
        return client;
    }

    public String getNumber() {
        return number;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
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

    public Money getAmountPaid() {
        return Money.ofMinor(amountPaidMinor, currency);
    }

    public Money getAmountCredited() {
        return Money.ofMinor(amountCreditedMinor, currency);
    }

    public Money getAmountRefunded() {
        return Money.ofMinor(amountRefundedMinor, currency);
    }

    public Party getBillTo() {
        return billTo;
    }

    public Party getSeller() {
        return seller;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public java.math.BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public String getRateSource() {
        return rateSource;
    }

    /** The total in the organization's base currency, as frozen at issue (null for drafts). */
    public Money getTotalBase() {
        return totalBaseMinor == null ? null : Money.ofMinor(totalBaseMinor, baseCurrency);
    }

    public UUID getQuoteId() {
        return quoteId;
    }

    public Invoice getCreditedInvoice() {
        return creditedInvoice;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** The name to show in lists: the frozen one once issued, the live client name while a draft. */
    public String displayClientName() {
        return billTo != null && billTo.getName() != null ? billTo.getName() : client.getName();
    }
}
