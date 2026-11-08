-- Each credit note line now points at the invoice line it credits, so a credit note can
-- only return what was invoiced: per original line, credited quantity <= its quantity and
-- credited amount <= its total (checked in InvoiceService). E-invoicing (JoFotara return
-- invoices) needs the same link. Credit notes issued before this stay unlinked.
ALTER TABLE invoice_lines ADD COLUMN credited_line_id UUID REFERENCES invoice_lines (id);
CREATE INDEX idx_invoice_lines_credited_line ON invoice_lines (credited_line_id) WHERE credited_line_id IS NOT NULL;
