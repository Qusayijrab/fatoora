-- Unguessable public links (CLAUDE.md rule 9): 256 random bits, base64url. Looked up
-- without a logged-in tenant, so this table is resolved first and then scopes every
-- further query to its organization. Used for invoice payment pages and (Phase 8)
-- the client portal.
CREATE TABLE public_links (
    token           VARCHAR(64) PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    kind            VARCHAR(20) NOT NULL CHECK (kind IN ('INVOICE', 'CLIENT_PORTAL')),
    target_id       UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_public_links_active ON public_links (organization_id, kind, target_id) WHERE revoked_at IS NULL;

-- One row per checkout started with a payment provider.
CREATE TABLE checkout_sessions (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    invoice_id          UUID         NOT NULL REFERENCES invoices (id),
    provider            VARCHAR(30)  NOT NULL,
    provider_session_id VARCHAR(200) NOT NULL,
    amount_minor        BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'EXPIRED')),
    checkout_url        TEXT         NOT NULL,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_checkout_provider_session UNIQUE (provider, provider_session_id)
);
CREATE INDEX idx_checkout_org_invoice ON checkout_sessions (organization_id, invoice_id);

-- Every webhook we accept, keyed by the provider's own event id (CLAUDE.md rule 8):
-- a retried or replayed delivery hits the unique constraint and is ignored.
CREATE TABLE webhook_events (
    id              UUID PRIMARY KEY,
    provider        VARCHAR(30)  NOT NULL,
    event_id        VARCHAR(200) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    organization_id UUID REFERENCES organizations (id) ON DELETE SET NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')),
    detail          TEXT,
    payload         JSONB        NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, event_id)
);
