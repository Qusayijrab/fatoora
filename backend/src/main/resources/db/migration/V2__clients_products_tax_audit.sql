-- Seller details printed on invoices (Phase 4) and edited on the Organization page.
ALTER TABLE organizations
    ADD COLUMN legal_name    VARCHAR(200),
    ADD COLUMN tax_id        VARCHAR(50),
    ADD COLUMN email         CITEXT,
    ADD COLUMN phone         VARCHAR(50),
    ADD COLUMN address_line1 VARCHAR(200),
    ADD COLUMN address_line2 VARCHAR(200),
    ADD COLUMN city          VARCHAR(100),
    ADD COLUMN country       CHAR(2) CHECK (country ~ '^[A-Z]{2}$');

-- Tax rates are percentages, not money, so an exact NUMERIC is right here.
CREATE TABLE tax_rates (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    rate            NUMERIC(6, 3) NOT NULL CHECK (rate >= 0 AND rate <= 100),
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tax_rates_org_name UNIQUE (organization_id, name)
);
-- At most one default rate per organization, enforced by the database.
CREATE UNIQUE INDEX uq_tax_rates_one_default ON tax_rates (organization_id) WHERE is_default;

CREATE TABLE clients (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    email           CITEXT,
    phone           VARCHAR(50),
    tax_id          VARCHAR(50),
    address_line1   VARCHAR(200),
    address_line2   VARCHAR(200),
    city            VARCHAR(100),
    region          VARCHAR(100),
    postal_code     VARCHAR(20),
    country         CHAR(2) CHECK (country ~ '^[A-Z]{2}$'),
    currency        CHAR(3) CHECK (currency ~ '^[A-Z]{3}$'),
    notes           TEXT,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_clients_org_name ON clients (organization_id, lower(name));

-- Money: amount in the currency's smallest unit (fils, cents) + ISO currency code.
CREATE TABLE products (
    id               UUID PRIMARY KEY,
    organization_id  UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    sku              VARCHAR(64),
    unit             VARCHAR(20)  NOT NULL DEFAULT 'item',
    unit_price_minor BIGINT       NOT NULL CHECK (unit_price_minor >= 0),
    currency         CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    tax_rate_id      UUID REFERENCES tax_rates (id),
    archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_org_name ON products (organization_id, lower(name));
CREATE UNIQUE INDEX uq_products_org_sku ON products (organization_id, sku) WHERE sku IS NOT NULL;

-- Append-only audit trail. No foreign key on entity_id: the log must outlive what it describes.
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    actor_user_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    entity_type     VARCHAR(40) NOT NULL,
    entity_id       UUID        NOT NULL,
    action          VARCHAR(40) NOT NULL,
    summary         TEXT        NOT NULL,
    data            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_org_time ON audit_events (organization_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_events (organization_id, entity_type, entity_id);

-- Audit rows can never be changed or deleted, even by buggy application code.
CREATE FUNCTION audit_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();

CREATE TRIGGER trg_audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW WHEN (pg_trigger_depth() = 0) EXECUTE FUNCTION audit_events_immutable();
