package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.common.money.MoneyDto;
import com.fatoora.invoicing.common.validation.CurrencyCode;
import com.fatoora.invoicing.document.DocumentDtos;
import com.fatoora.invoicing.document.DocumentDtos.ClientRef;
import com.fatoora.invoicing.document.DocumentDtos.DocumentRef;
import com.fatoora.invoicing.document.DocumentDtos.LineInput;
import com.fatoora.invoicing.document.DocumentDtos.LineResponse;
import com.fatoora.invoicing.document.DocumentDtos.PartyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {}

    /** Create or replace a draft. Missing currency/dates default to the client, the org and today. */
    public record InvoiceRequest(
            @NotNull UUID clientId,
            @CurrencyCode String currency,
            LocalDate issueDate,
            LocalDate dueDate,
            @Size(max = 5000) String notes,
            @Size(max = 5000) String terms,
            @NotEmpty(message = "add at least one line") @Size(max = 200) List<@Valid LineInput> lines) {}

    public record PaymentRequest(
            @NotNull @Pattern(regexp = DocumentDtos.AMOUNT_PATTERN, message = "must be an amount like 100 or 99.500") String amount,
            LocalDate paidOn,
            @NotNull Payment.Method method,
            @Size(max = 200) String reference) {}

    public record VoidRequest(@NotBlank @Size(max = 1000) String reason) {}

    /** keepDates: issue with the draft's own (past) dates instead of today's. */
    public record IssueRequest(Boolean keepDates) {}

    /**
     * Credits lines of the original invoice only. Each line names the invoice line it
     * credits and how many units; unitPrice is optional (default: the original price) and
     * may be lower, to credit a price reduction rather than a return.
     */
    public record CreditNoteRequest(
            @NotBlank @Size(max = 1000) String reason,
            @NotEmpty(message = "add at least one line") @Size(max = 200) List<@Valid CreditLineInput> lines) {}

    public record CreditLineInput(
            @NotNull UUID invoiceLineId,
            @NotNull @Pattern(regexp = DocumentDtos.QUANTITY_PATTERN, message = "must be a quantity like 1 or 2.5") String quantity,
            @Pattern(regexp = DocumentDtos.AMOUNT_PATTERN, message = "must be an amount like 100 or 99.500") String unitPrice) {}

    /** What is still left to credit on one invoice line. */
    public record CreditableLine(UUID lineId, String quantity, MoneyDto amount) {}

    public record PaymentResponse(
            UUID id, Payment.Kind kind, MoneyDto amount, Payment.Method method, LocalDate paidOn, String reference,
            String provider) {

        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getKind(), MoneyDto.from(p.getAmount()), p.getMethod(), p.getPaidOn(),
                    p.getReference(), p.getProvider());
        }
    }

    public record CreditNoteRef(UUID id, String number, MoneyDto total, LocalDate issueDate, String reason) {}

    public record InvoiceSummary(
            UUID id,
            InvoiceKind kind,
            String number,
            InvoiceStatus status,
            ClientRef client,
            String currency,
            LocalDate issueDate,
            LocalDate dueDate,
            MoneyDto total,
            MoneyDto balanceDue) {

        public static InvoiceSummary from(Invoice i) {
            return new InvoiceSummary(i.getId(), i.getKind(), i.getNumber(), i.getStatus(),
                    new ClientRef(i.getClient().getId(), i.displayClientName()), i.getCurrency(), i.getIssueDate(),
                    i.getDueDate(), MoneyDto.from(i.getTotal()), MoneyDto.from(i.balanceDue()));
        }
    }

    public record InvoiceResponse(
            UUID id,
            InvoiceKind kind,
            String number,
            InvoiceStatus status,
            ClientRef client,
            PartyResponse billTo,
            PartyResponse seller,
            String currency,
            LocalDate issueDate,
            LocalDate dueDate,
            String notes,
            String terms,
            List<LineResponse> lines,
            MoneyDto subtotal,
            MoneyDto tax,
            MoneyDto total,
            MoneyDto amountPaid,
            MoneyDto amountCredited,
            MoneyDto amountRefunded,
            MoneyDto balanceDue,
            MoneyDto refundDue,
            List<PaymentResponse> payments,
            List<CreditNoteRef> creditNotes,
            List<CreditableLine> creditable,
            DocumentRef creditedInvoice,
            UUID quoteId,
            String baseCurrency,
            String exchangeRate,
            String rateSource,
            MoneyDto totalBase,
            Instant issuedAt,
            Instant voidedAt,
            Instant firstViewedAt,
            Instant lastViewedAt,
            int viewCount,
            String voidReason,
            Instant createdAt) {

        static InvoiceResponse from(Invoice i, List<Payment> payments, List<Invoice> creditNotes,
                List<CreditableLine> creditable) {
            String cur = i.getCurrency();
            Invoice credited = i.getCreditedInvoice();
            return new InvoiceResponse(
                    i.getId(), i.getKind(), i.getNumber(), i.getStatus(),
                    new ClientRef(i.getClient().getId(), i.displayClientName()),
                    PartyResponse.from(i.getBillTo()), PartyResponse.from(i.getSeller()), cur, i.getIssueDate(),
                    i.getDueDate(), i.getNotes(), i.getTerms(),
                    i.getLines().stream().map(l -> LineResponse.from(l, cur)).toList(),
                    MoneyDto.from(i.getSubtotal()), MoneyDto.from(i.getTax()), MoneyDto.from(i.getTotal()),
                    MoneyDto.from(i.getAmountPaid()), MoneyDto.from(i.getAmountCredited()),
                    MoneyDto.from(i.getAmountRefunded()), MoneyDto.from(i.balanceDue()), MoneyDto.from(i.refundDue()),
                    payments.stream().map(PaymentResponse::from).toList(),
                    creditNotes.stream()
                            .map(cn -> new CreditNoteRef(cn.getId(), cn.getNumber(), MoneyDto.from(cn.getTotal()),
                                    cn.getIssueDate(), cn.getNotes()))
                            .toList(),
                    creditable,
                    credited == null ? null : new DocumentRef(credited.getId(), credited.getNumber()),
                    i.getQuoteId(), i.getBaseCurrency(),
                    i.getExchangeRate() == null ? null : i.getExchangeRate().stripTrailingZeros().toPlainString(),
                    i.getRateSource(), MoneyDto.from(i.getTotalBase()), i.getIssuedAt(), i.getVoidedAt(),
                    i.getFirstViewedAt(), i.getLastViewedAt(), i.getViewCount(), i.getVoidReason(), i.getCreatedAt());
        }
    }
}
