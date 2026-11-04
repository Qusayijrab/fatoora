-- Credit notes may now be raised on paid invoices, so paid + credited can exceed the
-- total: the excess is owed back to the client until a refund is recorded.
ALTER TABLE invoices
    ADD COLUMN amount_refunded_minor BIGINT NOT NULL DEFAULT 0 CHECK (amount_refunded_minor >= 0);

ALTER TABLE invoices DROP CONSTRAINT ck_invoices_paid_within_total;
ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_credited_within_total CHECK (amount_credited_minor <= total_minor),
    ADD CONSTRAINT ck_invoices_refunded_within_paid CHECK (amount_refunded_minor <= amount_paid_minor),
    ADD CONSTRAINT ck_invoices_net_paid_within_total CHECK (amount_paid_minor - amount_refunded_minor <= total_minor);

-- Refunds are rows in payments with kind = REFUND (amount stays positive).
ALTER TABLE payments
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'PAYMENT' CHECK (kind IN ('PAYMENT', 'REFUND'));
