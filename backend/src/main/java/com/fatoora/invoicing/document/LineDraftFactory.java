package com.fatoora.invoicing.document;

import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.error.ApiException;
import com.fatoora.invoicing.common.money.Money;
import com.fatoora.invoicing.document.DocumentDtos.LineInput;
import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineCalculator.LineDraft;
import com.fatoora.invoicing.product.ProductService;
import com.fatoora.invoicing.tax.TaxRate;
import com.fatoora.invoicing.tax.TaxRateService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns typed lines into calculated lines for one document currency. Resolves product
 * and tax rate ids inside the current organization (a foreign id is "not found") and
 * copies the tax rate value onto the line.
 */
@Component
public class LineDraftFactory {

    private final TaxRateService taxRates;
    private final ProductService products;

    public LineDraftFactory(TaxRateService taxRates, ProductService products) {
        this.taxRates = taxRates;
        this.products = products;
    }

    public List<CalculatedLine> calculate(List<LineInput> inputs, String currency) {
        List<CalculatedLine> lines = new ArrayList<>(inputs.size());
        for (LineInput in : inputs) {
            if (in.productId() != null) {
                products.find(in.productId());
            }
            BigDecimal rate = BigDecimal.ZERO;
            if (in.taxRateId() != null) {
                TaxRate taxRate = taxRates.find(in.taxRateId());
                rate = taxRate.getRate();
            }
            BigDecimal quantity = new BigDecimal(in.quantity());
            if (quantity.signum() <= 0) {
                throw ApiException.badRequest("Quantity must be more than zero");
            }
            lines.add(LineCalculator.calculate(new LineDraft(in.productId(), in.description().trim(), quantity,
                    Strings.clean(in.unit()), Money.parse(in.unitPrice(), currency), in.taxRateId(), rate)));
        }
        return lines;
    }
}
