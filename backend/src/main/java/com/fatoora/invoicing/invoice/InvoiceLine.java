package com.fatoora.invoicing.invoice;

import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineFields;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine extends LineFields {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    /** On a credit note line: the invoice line it credits. */
    @Column(name = "credited_line_id", updatable = false)
    private UUID creditedLineId;

    protected InvoiceLine() {}

    InvoiceLine(Invoice invoice, int position, CalculatedLine line) {
        this.invoice = invoice;
        fill(position, line);
    }

    void creditsLine(UUID originalLineId) {
        this.creditedLineId = originalLineId;
    }

    public UUID getCreditedLineId() {
        return creditedLineId;
    }
}
