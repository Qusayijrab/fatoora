package com.fatoora.invoicing.document;

import com.fatoora.invoicing.common.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The one place document money is computed. Pure functions, no Spring, fully unit-tested.
 *
 * <p>Rules (CLAUDE.md rule 1):
 * <ul>
 *   <li>line subtotal = unit price x quantity, rounded HALF_UP to the minor unit</li>
 *   <li>line tax = line subtotal x rate%, rounded HALF_UP <b>per line</b></li>
 *   <li>document subtotal / tax / total = plain sums of the rounded line values</li>
 * </ul>
 * Rounding per line (not on the document total) means each printed line adds up on its
 * own, and the document total always equals the sum of what the client sees.
 */
public final class LineCalculator {

    private LineCalculator() {}

    /** One line before calculation: what the user typed (already parsed into Money and BigDecimal). */
    public record LineDraft(
            UUID productId, String description, BigDecimal quantity, String unit, Money unitPrice, UUID taxRateId,
            BigDecimal taxRate) {}

    public record CalculatedLine(
            UUID productId, String description, BigDecimal quantity, String unit, Money unitPrice, UUID taxRateId,
            BigDecimal taxRate, Money subtotal, Money tax, Money total) {}

    public record Totals(Money subtotal, Money tax, Money total) {}

    public static CalculatedLine calculate(LineDraft d) {
        if (d.quantity().signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal rate = d.taxRate() == null ? BigDecimal.ZERO : d.taxRate();
        Money subtotal = d.unitPrice().times(d.quantity());
        Money tax = subtotal.percent(rate);
        return new CalculatedLine(d.productId(), d.description(), d.quantity(), d.unit(), d.unitPrice(),
                d.taxRateId(), rate, subtotal, tax, subtotal.plus(tax));
    }

    public static Totals totals(List<CalculatedLine> lines, String currency) {
        Money subtotal = Money.zero(currency);
        Money tax = Money.zero(currency);
        for (CalculatedLine line : lines) {
            subtotal = subtotal.plus(line.subtotal());
            tax = tax.plus(line.tax());
        }
        return new Totals(subtotal, tax, subtotal.plus(tax));
    }
}
