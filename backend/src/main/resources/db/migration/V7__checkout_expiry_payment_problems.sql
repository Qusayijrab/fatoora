-- Checkouts now expire: when a new one starts for the same invoice, when the balance
-- changes (manual payment, credit note, void), or at expires_at. An expired checkout can
-- no longer take money, so a client cannot pay twice for one balance.
ALTER TABLE checkout_sessions ADD COLUMN expires_at TIMESTAMPTZ;
CREATE INDEX idx_checkout_open ON checkout_sessions (organization_id, invoice_id) WHERE status = 'OPEN';

-- A provider event that took money we could not apply (invoice already paid or voided,
-- amount over the balance) is shown to the business until someone marks it handled,
-- usually after refunding the client in the provider's dashboard.
ALTER TABLE webhook_events
    ADD COLUMN invoice_id  UUID REFERENCES invoices (id),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by UUID REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_webhook_events_unresolved ON webhook_events (organization_id, received_at)
    WHERE invoice_id IS NOT NULL AND status IN ('FAILED', 'IGNORED') AND resolved_at IS NULL;
