-- When the client opened an issued invoice: through the payment page, its PDF, or the
-- client portal. Lets the business see "viewed 3 times, last on 5 Nov" before chasing.
ALTER TABLE invoices
    ADD COLUMN first_viewed_at TIMESTAMPTZ,
    ADD COLUMN last_viewed_at  TIMESTAMPTZ,
    ADD COLUMN view_count      INT NOT NULL DEFAULT 0 CHECK (view_count >= 0);
