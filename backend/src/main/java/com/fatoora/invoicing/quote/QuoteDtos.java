package com.fatoora.invoicing.quote;

import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.document.DocumentDtos.ClientRef;
import com.fatoora.invoicing.document.DocumentDtos.LineInput;
import com.fatoora.invoicing.document.DocumentDtos.LineResponse;
import com.fatoora.invoicing.document.DocumentDtos.PartyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {}

    public record QuoteRequest(
            @NotNull UUID clientId,
            @CurrencyCode String currency,
            LocalDate issueDate,
            LocalDate validUntil,
            @Size(max = 5000) String notes,
            @Size(max = 5000) String terms,
            @NotEmpty(message = "add at least one line") @Size(max = 200) List<@Valid LineInput> lines) {}

    public record QuoteSummary(
            UUID id, String number, QuoteStatus status, ClientRef client, String currency, LocalDate issueDate,
            LocalDate validUntil, MoneyDto total, UUID convertedInvoiceId) {

        static QuoteSummary from(Quote q) {
            return new QuoteSummary(q.getId(), q.getNumber(), q.getStatus(),
                    new ClientRef(q.getClient().getId(), q.displayClientName()), q.getCurrency(), q.getIssueDate(),
                    q.getValidUntil(), MoneyDto.from(q.getTotal()), q.getConvertedInvoiceId());
        }
    }

    public record QuoteResponse(
            UUID id,
            String number,
            QuoteStatus status,
            ClientRef client,
            PartyResponse billTo,
            String currency,
            LocalDate issueDate,
            LocalDate validUntil,
            String notes,
            String terms,
            List<LineResponse> lines,
            MoneyDto subtotal,
            MoneyDto tax,
            MoneyDto total,
            Instant sentAt,
            Instant acceptedAt,
            Instant rejectedAt,
            UUID convertedInvoiceId,
            Instant createdAt) {

        static QuoteResponse from(Quote q) {
            String cur = q.getCurrency();
            return new QuoteResponse(q.getId(), q.getNumber(), q.getStatus(),
                    new ClientRef(q.getClient().getId(), q.displayClientName()), PartyResponse.from(q.getBillTo()), cur,
                    q.getIssueDate(), q.getValidUntil(), q.getNotes(), q.getTerms(),
                    q.getLines().stream().map(l -> LineResponse.from(l, cur)).toList(),
                    MoneyDto.from(q.getSubtotal()), MoneyDto.from(q.getTax()), MoneyDto.from(q.getTotal()),
                    q.getSentAt(), q.getAcceptedAt(), q.getRejectedAt(), q.getConvertedInvoiceId(), q.getCreatedAt());
        }
    }
}
