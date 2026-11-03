-- Exchange rates (CLAUDE.md rule 6). An invoice in a foreign currency freezes the rate
-- and its base-currency total at the moment it is issued; reports use those frozen
-- values forever, so last year's numbers never move when rates do.
ALTER TABLE invoices
    ADD COLUMN base_currency    CHAR(3) CHECK (base_currency ~ '^[A-Z]{3}$'),
    ADD COLUMN exchange_rate    NUMERIC(20, 10) CHECK (exchange_rate > 0),
    ADD COLUMN total_base_minor BIGINT,
    ADD COLUMN rate_source      VARCHAR(20);

-- Invoices issued before this migration: frozen in their own currency at rate 1.
-- Reports only add up rows whose base currency is the organization's, so these never
-- get mixed into another currency's totals.
UPDATE invoices
SET base_currency = currency, exchange_rate = 1, total_base_minor = total_minor, rate_source = 'LEGACY'
WHERE status <> 'DRAFT';

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_rate_after_draft
        CHECK (status = 'DRAFT' OR (base_currency IS NOT NULL AND exchange_rate IS NOT NULL AND total_base_minor IS NOT NULL));

-- Rates an organization entered itself: "1 unit of currency = rate units of base".
CREATE TABLE exchange_rates (
    id              UUID PRIMARY KEY,
    organization_id UUID            NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    currency        CHAR(3)         NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    base_currency   CHAR(3)         NOT NULL CHECK (base_currency ~ '^[A-Z]{3}$'),
    rate            NUMERIC(20, 10) NOT NULL CHECK (rate > 0),
    effective_date  DATE            NOT NULL,
    source          VARCHAR(20)     NOT NULL CHECK (source IN ('MANUAL', 'ECB')),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_exchange_rates UNIQUE (organization_id, currency, base_currency, effective_date, source)
);
CREATE INDEX idx_exchange_rates_lookup ON exchange_rates (organization_id, currency, base_currency, effective_date DESC);
