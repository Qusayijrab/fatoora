package com.fatoora.invoicing.recurring;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import com.fatoora.invoicing.document.LineCalculator;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import com.fatoora.invoicing.document.LineCalculator.Totals;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A schedule that turns the same lines into a new invoice every period. */
@Entity
@Table(name = "recurring_templates")
public class RecurringTemplate extends TenantScopedEntity {

    public enum Status {
        ACTIVE,
        PAUSED,
        ENDED
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(name = "interval_count", nullable = false)
    private int intervalCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @Column(name = "run_count", nullable = false)
    private int runCount;

    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    @Column(name = "auto_issue", nullable = false)
    private boolean autoIssue;

    @Column(name = "auto_email", nullable = false)
    private boolean autoEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

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

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RecurringTemplateLine> lines = new ArrayList<>();

    protected RecurringTemplate() {}

    public static RecurringTemplate create(Client client, String name, String currency, Frequency frequency,
            int intervalCount, LocalDate startDate) {
        RecurringTemplate t = new RecurringTemplate();
        t.client = client;
        t.name = name;
        t.currency = currency;
        t.frequency = frequency;
        t.intervalCount = intervalCount;
        t.startDate = startDate;
        t.status = Status.ACTIVE;
        return t;
    }

    /**
     * Edits apply to future invoices only. Changing the schedule re-anchors it: the next
     * run becomes the first scheduled date on or after {@code today}.
     */
    void update(Client client, String name, String currency, Frequency frequency, int intervalCount, LocalDate startDate,
            LocalDate endDate, int paymentTermsDays, boolean autoIssue, boolean autoEmail, String notes, String terms,
            List<CalculatedLine> newLines, LocalDate today) {
        if (status == Status.ENDED) {
            throw ApiException.conflict("This recurring invoice has ended. Create a new one instead.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.badRequest("The end date cannot be before the start date");
        }
        boolean scheduleChanged = this.frequency != frequency || this.intervalCount != intervalCount
                || !this.startDate.equals(startDate);
        this.client = client;
        this.name = name;
        this.currency = currency;
        this.frequency = frequency;
        this.intervalCount = intervalCount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.paymentTermsDays = paymentTermsDays;
        this.autoIssue = autoIssue;
        this.autoEmail = autoEmail && autoIssue;
        this.notes = notes;
        this.terms = terms;
        lines.clear();
        for (int i = 0; i < newLines.size(); i++) {
            lines.add(new RecurringTemplateLine(this, i + 1, newLines.get(i)));
        }
        Totals totals = LineCalculator.totals(newLines, currency);
        this.subtotalMinor = totals.subtotal().amountMinor();
        this.taxMinor = totals.tax().amountMinor();
        this.totalMinor = totals.total().amountMinor();
        // New templates (never run) and schedule changes start at the first date on or after today:
        // a start date in the past never back-bills.
        if (scheduleChanged || (runCount == 0 && lastRunAt == null)) {
            reanchor(today);
        }
        endIfPastEndDate();
    }

    private void reanchor(LocalDate today) {
        int n = 0;
        LocalDate date = startDate;
        while (date.isBefore(today) && n < 10_000) {
            n++;
            date = frequency.nthDate(startDate, n, intervalCount);
        }
        this.runCount = n;
        this.nextRunDate = date;
    }

    /** Lines for the next invoice, priced and taxed exactly as saved on the template. */
    List<CalculatedLine> invoiceLines() {
        return lines.stream()
                .map(l -> LineCalculator.calculate(new LineDraft(l.getProductId(), l.getDescription(), l.getQuantity(),
                        l.getUnit(), l.unitPrice(currency), l.getTaxRateId(), l.getTaxRate())))
                .toList();
    }

    boolean isDue(LocalDate today) {
        return status == Status.ACTIVE && nextRunDate != null && !nextRunDate.isAfter(today);
    }

    /** Moves to the next period after a run (or after finding the period already generated). */
    void advance(Instant now) {
        runCount++;
        lastRunAt = now;
        nextRunDate = frequency.nthDate(startDate, runCount, intervalCount);
        endIfPastEndDate();
    }

    private void endIfPastEndDate() {
        if (endDate != null && nextRunDate != null && nextRunDate.isAfter(endDate)) {
            status = Status.ENDED;
            nextRunDate = null;
        }
    }

    void pause() {
        if (status != Status.ACTIVE) {
            throw ApiException.conflict("Only active recurring invoices can be paused");
        }
        status = Status.PAUSED;
    }

    /** Resuming skips periods that passed while paused: no back-billing. */
    void resume(LocalDate today) {
        if (status != Status.PAUSED) {
            throw ApiException.conflict("Only paused recurring invoices can be resumed");
        }
        status = Status.ACTIVE;
        while (nextRunDate != null && nextRunDate.isBefore(today)) {
            runCount++;
            nextRunDate = frequency.nthDate(startDate, runCount, intervalCount);
        }
        endIfPastEndDate();
    }

    void end() {
        status = Status.ENDED;
        nextRunDate = null;
    }

    public Client getClient() {
        return client;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public int getRunCount() {
        return runCount;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public boolean isAutoIssue() {
        return autoIssue;
    }

    public boolean isAutoEmail() {
        return autoEmail;
    }

    public Status getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getTerms() {
        return terms;
    }

    public Money getTotal() {
        return Money.ofMinor(totalMinor, currency);
    }

    public Money getSubtotal() {
        return Money.ofMinor(subtotalMinor, currency);
    }

    public Money getTax() {
        return Money.ofMinor(taxMinor, currency);
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public List<RecurringTemplateLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
