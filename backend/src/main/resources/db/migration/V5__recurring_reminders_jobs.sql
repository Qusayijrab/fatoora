-- ShedLock: one row per job name; whoever holds the row runs the job (multi-instance safe).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Reminder settings per organization: on/off and which days after the due date.
ALTER TABLE organizations
    ADD COLUMN reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN reminder_days     INT[]   NOT NULL DEFAULT '{1,7,14}';

-- A recurring invoice: lines + a schedule. Run dates are computed from the anchor
-- (start_date + n x interval), never from the previous run, so the 31st stays the
-- month's last day instead of drifting to the 28th after February.
CREATE TABLE recurring_templates (
    id                 UUID PRIMARY KEY,
    organization_id    UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id          UUID         NOT NULL REFERENCES clients (id),
    name               VARCHAR(200) NOT NULL,
    currency           CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    frequency          VARCHAR(20)  NOT NULL CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    interval_count     INT          NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 12),
    start_date         DATE         NOT NULL,
    end_date           DATE,
    next_run_date      DATE,
    run_count          INT          NOT NULL DEFAULT 0,
    payment_terms_days INT          NOT NULL DEFAULT 30 CHECK (payment_terms_days BETWEEN 0 AND 365),
    auto_issue         BOOLEAN      NOT NULL DEFAULT TRUE,
    auto_email         BOOLEAN      NOT NULL DEFAULT FALSE,
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    notes              TEXT,
    terms              TEXT,
    subtotal_minor     BIGINT       NOT NULL DEFAULT 0,
    tax_minor          BIGINT       NOT NULL DEFAULT 0,
    total_minor        BIGINT       NOT NULL DEFAULT 0,
    last_run_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_recurring_due ON recurring_templates (status, next_run_date);

CREATE TABLE recurring_template_lines (
    id               UUID PRIMARY KEY,
    organization_id  UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    template_id      UUID           NOT NULL REFERENCES recurring_templates (id) ON DELETE CASCADE,
    position         INT            NOT NULL,
    product_id       UUID REFERENCES products (id),
    description      TEXT           NOT NULL,
    quantity         NUMERIC(12, 3) NOT NULL CHECK (quantity > 0),
    unit             VARCHAR(20),
    unit_price_minor BIGINT         NOT NULL CHECK (unit_price_minor >= 0),
    tax_rate_id      UUID REFERENCES tax_rates (id),
    tax_rate         NUMERIC(6, 3)  NOT NULL DEFAULT 0,
    subtotal_minor   BIGINT         NOT NULL,
    tax_minor        BIGINT         NOT NULL,
    total_minor      BIGINT         NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_recurring_lines_position UNIQUE (template_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- One row per generated period (CLAUDE.md rule 7). If the job runs twice, crashes and
-- retries, or two servers race, the second insert for the same period is a no-op.
CREATE TABLE recurring_runs (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    template_id     UUID        NOT NULL REFERENCES recurring_templates (id) ON DELETE CASCADE,
    period_date     DATE        NOT NULL,
    invoice_id      UUID REFERENCES invoices (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recurring_runs_period UNIQUE (template_id, period_date)
);

-- One row per reminder sent (same idea): an invoice never gets the "7 days overdue"
-- reminder twice.
CREATE TABLE invoice_reminders (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    invoice_id      UUID        NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    days_overdue    INT         NOT NULL,
    sent_to         CITEXT      NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_reminders UNIQUE (invoice_id, days_overdue)
);
