package com.fatoora.invoicing.recurring;

import com.fatoora.invoicing.document.LineCalculator.CalculatedLine;
import com.fatoora.invoicing.document.LineFields;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recurring_template_lines")
public class RecurringTemplateLine extends LineFields {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false, updatable = false)
    private RecurringTemplate template;

    protected RecurringTemplateLine() {}

    RecurringTemplateLine(RecurringTemplate template, int position, CalculatedLine line) {
        this.template = template;
        fill(position, line);
    }
}
