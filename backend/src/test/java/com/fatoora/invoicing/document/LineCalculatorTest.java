package com.fatoora.invoicing.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineCalculatorTest {

    static CalculatedLine line(String price, String currency, String qty, String rate) {
        return LineCalculator.calculate(new LineDraft(null, "x", new BigDecimal(qty), null, Money.parse(price, currency),
                null, rate == null ? null : new BigDecimal(rate)));
    }

    @Test
    void jordanianInvoiceAddsUpToTheFils() {
        var design = line("850", "JOD", "1", "16");
        var copy = line("25.000", "JOD", "12", "16");
        var totals = LineCalculator.totals(List.of(design, copy), "JOD");

        assertThat(design.subtotal().toPlainString()).isEqualTo("850.000");
        assertThat(copy.subtotal().toPlainString()).isEqualTo("300.000");
        assertThat(totals.subtotal().toPlainString()).isEqualTo("1150.000");
        assertThat(totals.tax().toPlainString()).isEqualTo("184.000");
        assertThat(totals.total().toPlainString()).isEqualTo("1334.000");
    }

    @Test
    void taxIsRoundedPerLineNotOnTheTotal() {
        // Each line: 0.25 USD x 10% = 0.025 -> 0.03 (HALF_UP). Three lines -> 0.09.
        // Rounding the document total instead would give 0.075 -> 0.08.
        var lines = List.of(line("0.25", "USD", "1", "10"), line("0.25", "USD", "1", "10"), line("0.25", "USD", "1", "10"));
        var totals = LineCalculator.totals(lines, "USD");

        assertThat(totals.tax().toPlainString()).isEqualTo("0.09");
        assertThat(totals.total().toPlainString()).isEqualTo("0.84");
    }

    @Test
    void fractionalQuantitiesRoundHalfUp() {
        // 33.333 JOD x 1.5 h = 49.9995 -> 50.000
        assertThat(line("33.333", "JOD", "1.5", null).subtotal().toPlainString()).isEqualTo("50.000");
        // 0.07 USD x 0.5 = 0.035 -> 0.04
        assertThat(line("0.07", "USD", "0.5", null).subtotal().toPlainString()).isEqualTo("0.04");
    }

    @Test
    void noTaxRateMeansZeroTax() {
        var l = line("100", "USD", "2", null);
        assertThat(l.tax().isZero()).isTrue();
        assertThat(l.total().toPlainString()).isEqualTo("200.00");
    }

    @Test
    void zeroDecimalCurrencies() {
        var totals = LineCalculator.totals(List.of(line("1999", "JPY", "3", "10")), "JPY");
        assertThat(totals.subtotal().toPlainString()).isEqualTo("5997");
        assertThat(totals.tax().toPlainString()).isEqualTo("600"); // 599.7 -> 600
        assertThat(totals.total().toPlainString()).isEqualTo("6597");
    }

    @Test
    void quantityMustBePositive() {
        assertThatThrownBy(() -> line("1", "USD", "0", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyDocumentIsZero() {
        var totals = LineCalculator.totals(List.of(), "JOD");
        assertThat(totals.total().toPlainString()).isEqualTo("0.000");
    }

    @Test
    void numbersAreFormattedWithFourDigits() {
        assertThat(DocumentNumberService.format(DocumentNumberService.DocType.INVOICE, 2026, 1)).isEqualTo("INV-2026-0001");
        assertThat(DocumentNumberService.format(DocumentNumberService.DocType.QUOTE, 2026, 42)).isEqualTo("QUO-2026-0042");
        assertThat(DocumentNumberService.format(DocumentNumberService.DocType.CREDIT_NOTE, 2027, 12345)).isEqualTo("CN-2027-12345");
    }
}
