package com.fatoora.invoicing.recurring;

import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.document.DocumentDtos.ClientRef;
import com.fatoora.invoicing.document.DocumentDtos.LineInput;
import com.fatoora.invoicing.document.DocumentDtos.LineResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecurringDtos {

    private RecurringDtos() {}

    public record RecurringRequest(
            @NotNull UUID clientId,
            @NotBlank @Size(max = 200) String name,
            @CurrencyCode String currency,
            @NotNull Frequency frequency,
            @Min(1) @Max(12) Integer intervalCount,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Min(0) @Max(365) Integer paymentTermsDays,
            boolean autoIssue,
            boolean autoEmail,
            @Size(max = 5000) String notes,
            @Size(max = 5000) String terms,
            @NotEmpty(message = "add at least one line") @Size(max = 200) List<@Valid LineInput> lines) {}

    public record RunResponse(LocalDate periodDate, UUID invoiceId, String invoiceNumber, String invoiceStatus, Instant createdAt) {}

    public record RecurringResponse(
            UUID id,
            String name,
            ClientRef client,
            String currency,
            Frequency frequency,
            int intervalCount,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRunDate,
            int runCount,
            int paymentTermsDays,
            boolean autoIssue,
            boolean autoEmail,
            RecurringTemplate.Status status,
            int failureCount,
            String lastError,
            Instant lastFailedAt,
            boolean pausedByFailure,
            String notes,
            String terms,
            List<LineResponse> lines,
            MoneyDto subtotal,
            MoneyDto tax,
            MoneyDto total,
            Instant lastRunAt) {

        static RecurringResponse from(RecurringTemplate t) {
            String cur = t.getCurrency();
            return new RecurringResponse(t.getId(), t.getName(), new ClientRef(t.getClient().getId(), t.getClient().getName()),
                    cur, t.getFrequency(), t.getIntervalCount(), t.getStartDate(), t.getEndDate(), t.getNextRunDate(),
                    t.getRunCount(), t.getPaymentTermsDays(), t.isAutoIssue(), t.isAutoEmail(), t.getStatus(),
                    t.getFailureCount(), t.getLastError(), t.getLastFailedAt(), t.isPausedByFailure(), t.getNotes(),
                    t.getTerms(), t.getLines().stream().map(l -> LineResponse.from(l, cur)).toList(),
                    MoneyDto.from(t.getSubtotal()), MoneyDto.from(t.getTax()), MoneyDto.from(t.getTotal()), t.getLastRunAt());
        }
    }
}
