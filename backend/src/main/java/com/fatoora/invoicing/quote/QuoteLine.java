package com.fatoora.invoicing.quote;

import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineFields;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quote_lines")
public class QuoteLine extends LineFields {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private Quote quote;

    protected QuoteLine() {}

    QuoteLine(Quote quote, int position, CalculatedLine line) {
        this.quote = quote;
        fill(position, line);
    }
}
