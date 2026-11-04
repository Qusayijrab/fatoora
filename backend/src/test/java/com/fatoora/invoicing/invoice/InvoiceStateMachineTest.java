package com.fatoora.invoicing.invoice;

import static com.fatoora.invoicing.invoice.InvoiceStatus.DRAFT;
import static com.fatoora.invoicing.invoice.InvoiceStatus.ISSUED;
import static com.fatoora.invoicing.invoice.InvoiceStatus.OVERDUE;
import static com.fatoora.invoicing.invoice.InvoiceStatus.PAID;
import static com.fatoora.invoicing.invoice.InvoiceStatus.PARTIALLY_PAID;
import static com.fatoora.invoicing.invoice.InvoiceStatus.VOID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.document.LineCalculator;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import com.fatoora.invoicing.organization.Organization;
import com.fatoora.invoicing.document.Party;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** The invoice lifecycle without Spring or a database: pure domain rules. */
class InvoiceStateMachineTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    static Client client() {
        return new Client(new ClientRequest("Petra Design Studio", null, null, "JO-1", null, null, "Amman", null, null,
                "JO", null, null));
    }

    /** A 100.000 JOD draft due in 30 days. */
    static Invoice draft() {
        Invoice invoice = Invoice.draft(client(), "JOD", TODAY, TODAY.plusDays(30));
        invoice.edit(invoice.getClient(), "JOD", TODAY, TODAY.plusDays(30), null, null, List.of(
                LineCalculator.calculate(new LineDraft(null, "Work", BigDecimal.ONE, null, Money.parse("100", "JOD"), null, null))));
        return invoice;
    }

    static Invoice issued() {
        Invoice invoice = draft();
        invoice.issue("INV-2026-0001", Party.of(new Organization("Seller", "JOD", "Asia/Amman")), NOW, "JOD", BigDecimal.ONE, "SAME");
        return invoice;
    }

    static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Nested
    class TransitionTable {
        @Test
        void allowedMoves() {
            assertThat(DRAFT.canMoveTo(ISSUED)).isTrue();
            assertThat(ISSUED.canMoveTo(PARTIALLY_PAID)).isTrue();
            assertThat(ISSUED.canMoveTo(OVERDUE)).isTrue();
            assertThat(PARTIALLY_PAID.canMoveTo(PAID)).isTrue();
            assertThat(OVERDUE.canMoveTo(PAID)).isTrue();
            for (InvoiceStatus s : List.of(DRAFT, ISSUED, PARTIALLY_PAID, OVERDUE)) {
                assertThat(s.canMoveTo(VOID)).as("%s -> VOID", s).isTrue();
            }
        }

        @Test
        void forbiddenMoves() {
            assertThat(DRAFT.canMoveTo(PAID)).isFalse();
            assertThat(PAID.canMoveTo(VOID)).isFalse();
            assertThat(VOID.canMoveTo(ISSUED)).isFalse();
            assertThat(PAID.canMoveTo(ISSUED)).isFalse();
            assertThat(PARTIALLY_PAID.canMoveTo(ISSUED)).isFalse();
        }
    }

    @Nested
    class Issuing {
        @Test
        void issueFreezesNumberAndClient() {
            Invoice invoice = issued();
            assertThat(invoice.getStatus()).isEqualTo(ISSUED);
            assertThat(invoice.getNumber()).isEqualTo("INV-2026-0001");
            assertThat(invoice.getBillTo().getName()).isEqualTo("Petra Design Studio");
            assertThat(invoice.getSeller().getName()).isEqualTo("Seller");
        }

        @Test
        void issuedInvoicesCannotBeEdited() {
            Invoice invoice = issued();
            assertConflict(() -> invoice.edit(invoice.getClient(), "JOD", TODAY, TODAY, null, null, List.of()));
        }

        @Test
        void cannotIssueTwice() {
            Invoice invoice = issued();
            assertConflict(() -> invoice.issue("INV-2026-0002", null, NOW, "JOD", BigDecimal.ONE, "SAME"));
        }

        @Test
        void anOldDraftIsIssuedDatedTodayWithTheSameTerms() {
            Invoice invoice = Invoice.draft(client(), "JOD", TODAY.minusDays(40), TODAY.minusDays(10)); // 30-day terms
            invoice.dateForIssue(TODAY, false);
            assertThat(invoice.getIssueDate()).isEqualTo(TODAY);
            assertThat(invoice.getDueDate()).isEqualTo(TODAY.plusDays(30));
            // so it is not overdue the moment it is issued
            assertThat(invoice.getDueDate()).isAfter(TODAY);
        }

        @Test
        void aDecemberDraftIssuedInJanuaryGetsTheNewYear() {
            LocalDate jan2 = LocalDate.of(2027, 1, 2);
            Invoice invoice = Invoice.draft(client(), "JOD", LocalDate.of(2026, 12, 20), null);
            invoice.dateForIssue(jan2, false);
            assertThat(invoice.getIssueDate().getYear()).isEqualTo(2027);
            assertThat(invoice.getDueDate()).isNull();
        }

        @Test
        void keepingTheDraftsDatesIsAnExplicitChoiceAndNeverInTheFuture() {
            Invoice past = Invoice.draft(client(), "JOD", TODAY.minusDays(5), TODAY.plusDays(25));
            past.dateForIssue(TODAY, true);
            assertThat(past.getIssueDate()).isEqualTo(TODAY.minusDays(5));
            assertThat(past.getDueDate()).isEqualTo(TODAY.plusDays(25));

            Invoice paperFromLastYear = Invoice.draft(client(), "JOD", LocalDate.of(2025, 12, 31), null);
            paperFromLastYear.dateForIssue(TODAY, true);
            assertThat(paperFromLastYear.getIssueDate()).isEqualTo(LocalDate.of(2025, 12, 31));

            Invoice future = Invoice.draft(client(), "JOD", TODAY.plusDays(1), null);
            assertThatThrownBy(() -> future.dateForIssue(TODAY, true)).isInstanceOf(ApiException.class);
        }

        @Test
        void issuedInvoicesCannotBeRedated() {
            assertConflict(() -> issued().dateForIssue(TODAY.plusDays(1), false));
        }

        @Test
        void emptyDraftCannotBeIssued() {
            Invoice invoice = Invoice.draft(client(), "JOD", TODAY, TODAY);
            assertThatThrownBy(invoice::requireIssuable).isInstanceOf(ApiException.class);
        }
    }

    @Nested
    class Payments {
        @Test
        void partialThenFull() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("40", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(PARTIALLY_PAID);
            assertThat(invoice.balanceDue().toPlainString()).isEqualTo("60.000");

            invoice.recordPayment(Money.parse("60", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(PAID);
            assertThat(invoice.balanceDue().isZero()).isTrue();
        }

        @Test
        void cannotOverpayOrPayInAnotherCurrency() {
            Invoice invoice = issued();
            assertThatThrownBy(() -> invoice.recordPayment(Money.parse("100.001", "JOD"))).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> invoice.recordPayment(Money.parse("10", "USD"))).isInstanceOf(ApiException.class);
        }

        @Test
        void cannotPayADraftOrAPaidInvoice() {
            assertConflict(() -> draft().recordPayment(Money.parse("1", "JOD")));
            Invoice paid = issued();
            paid.recordPayment(Money.parse("100", "JOD"));
            assertConflict(() -> paid.recordPayment(Money.parse("1", "JOD")));
        }
    }

    @Nested
    class Overdue {
        @Test
        void becomesOverdueOnlyAfterTheDueDate() {
            Invoice invoice = issued(); // due TODAY + 30
            assertThat(invoice.markOverdueIfLate(TODAY.plusDays(30))).isFalse();
            assertThat(invoice.markOverdueIfLate(TODAY.plusDays(31))).isTrue();
            assertThat(invoice.getStatus()).isEqualTo(OVERDUE);
        }

        @Test
        void partialPaymentKeepsOverdueAndFullPaymentSettles() {
            Invoice invoice = issued();
            invoice.markOverdueIfLate(TODAY.plusDays(31));
            invoice.recordPayment(Money.parse("30", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(OVERDUE);
            invoice.recordPayment(Money.parse("70", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(PAID);
        }

        @Test
        void draftsAndPaidInvoicesNeverGoOverdue() {
            Invoice d = draft();
            assertThat(d.markOverdueIfLate(TODAY.plusYears(1))).isFalse();
            Invoice paid = issued();
            paid.recordPayment(Money.parse("100", "JOD"));
            assertThat(paid.markOverdueIfLate(TODAY.plusYears(1))).isFalse();
        }
    }

    @Nested
    class Voiding {
        @Test
        void issuedAndPartiallyPaidCanBeVoided() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("10", "JOD"));
            invoice.voidInvoice("Wrong client", NOW);
            assertThat(invoice.getStatus()).isEqualTo(VOID);
            assertThat(invoice.getNumber()).isEqualTo("INV-2026-0001"); // keeps its number
        }

        @Test
        void paidInvoicesCannotBeVoided() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("100", "JOD"));
            assertConflict(() -> invoice.voidInvoice("too late", NOW));
        }

        @Test
        void voidIsFinal() {
            Invoice invoice = issued();
            invoice.voidInvoice("x", NOW);
            assertConflict(() -> invoice.recordPayment(Money.parse("1", "JOD")));
            assertConflict(() -> invoice.voidInvoice("again", NOW));
        }
    }

    @Nested
    class CreditNotes {
        @Test
        void creditNoteReducesTheBalance() {
            Invoice invoice = issued();
            invoice.applyCredit(Money.parse("25", "JOD"));
            assertThat(invoice.balanceDue().toPlainString()).isEqualTo("75.000");
            assertThat(invoice.getStatus()).isEqualTo(PARTIALLY_PAID);
            invoice.applyCredit(Money.parse("75", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(PAID);
        }

        @Test
        void creditNotesOnlyForIssuedInvoicesThatAreNotVoid() {
            assertConflict(() -> Invoice.creditNoteFor(draft(), TODAY, "x"));
            Invoice voided = issued();
            voided.voidInvoice("x", NOW);
            assertConflict(() -> Invoice.creditNoteFor(voided, TODAY, "x"));
        }

        @Test
        void aPaidInvoiceCanBeCreditedAndTheExcessIsOwedBack() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("100", "JOD"));
            Invoice.creditNoteFor(invoice, TODAY, "Returned goods"); // allowed now
            invoice.applyCredit(Money.parse("30", "JOD"));
            assertThat(invoice.getStatus()).isEqualTo(PAID);
            assertThat(invoice.balanceDue().isZero()).isTrue();
            assertThat(invoice.refundDue().toPlainString()).isEqualTo("30.000");

            invoice.recordRefund(Money.parse("30", "JOD"));
            assertThat(invoice.refundDue().isZero()).isTrue();
            assertThat(invoice.getAmountRefunded().toPlainString()).isEqualTo("30.000");
            assertThat(invoice.balanceDue().isZero()).isTrue();
        }

        @Test
        void creditingAPartlyPaidInvoiceInFullSettlesItAndOwesBackWhatWasPaid() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("10", "JOD"));
            invoice.applyCredit(Money.parse("100", "JOD")); // cancel the whole invoice
            assertThat(invoice.getStatus()).isEqualTo(PAID);
            assertThat(invoice.balanceDue().isZero()).isTrue();
            assertThat(invoice.refundDue().toPlainString()).isEqualTo("10.000");
        }

        @Test
        void cannotCreditMoreThanWasInvoicedNorRefundMoreThanIsOwed() {
            Invoice invoice = issued();
            invoice.recordPayment(Money.parse("100", "JOD"));
            invoice.applyCredit(Money.parse("60", "JOD"));
            assertThatThrownBy(() -> invoice.applyCredit(Money.parse("40.001", "JOD"))).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> invoice.recordRefund(Money.parse("60.001", "JOD"))).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> invoice.recordRefund(Money.parse("1", "USD"))).isInstanceOf(ApiException.class);

            Invoice unpaid = issued();
            assertThatThrownBy(() -> unpaid.recordRefund(Money.parse("1", "JOD"))).isInstanceOf(ApiException.class);
        }
    }
}
