-- One counter row per (organization, document type, year). Numbers are taken with a
-- single atomic INSERT ... ON CONFLICT DO UPDATE ... RETURNING, which row-locks the
-- counter until the issuing transaction ends: concurrent issues queue up, and a rolled
-- back issue gives its number back. Result: INV-2026-0001, 0002, ... with no gaps.
CREATE TABLE document_counters (
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    doc_type        VARCHAR(10) NOT NULL,
    year            INT         NOT NULL,
    last_value      INT         NOT NULL,
    PRIMARY KEY (organization_id, doc_type, year)
);

CREATE TABLE quotes (
    id                    UUID PRIMARY KEY,
    organization_id       UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id             UUID        NOT NULL REFERENCES clients (id),
    number                VARCHAR(30),
    status                VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    currency              CHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    issue_date            DATE        NOT NULL,
    valid_until           DATE,
    notes                 TEXT,
    terms                 TEXT,
    subtotal_minor        BIGINT      NOT NULL DEFAULT 0,
    tax_minor             BIGINT      NOT NULL DEFAULT 0,
    total_minor           BIGINT      NOT NULL DEFAULT 0,
    -- client details frozen when the quote is sent
    bill_to_name          VARCHAR(200),
    bill_to_email         CITEXT,
    bill_to_phone         VARCHAR(50),
    bill_to_tax_id        VARCHAR(50),
    bill_to_address_line1 VARCHAR(200),
    bill_to_address_line2 VARCHAR(200),
    bill_to_city          VARCHAR(100),
    bill_to_region        VARCHAR(100),
    bill_to_postal_code   VARCHAR(20),
    bill_to_country       CHAR(2),
    sent_at               TIMESTAMPTZ,
    accepted_at           TIMESTAMPTZ,
    rejected_at           TIMESTAMPTZ,
    converted_invoice_id  UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_quotes_org_number UNIQUE (organization_id, number),
    CONSTRAINT ck_quotes_number_after_draft CHECK (status = 'DRAFT' OR number IS NOT NULL)
);
CREATE INDEX idx_quotes_org_status ON quotes (organization_id, status);
CREATE INDEX idx_quotes_org_client ON quotes (organization_id, client_id);

CREATE TABLE invoices (
    id                    UUID PRIMARY KEY,
    organization_id       UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    kind                  VARCHAR(20) NOT NULL DEFAULT 'INVOICE' CHECK (kind IN ('INVOICE', 'CREDIT_NOTE')),
    client_id             UUID        NOT NULL REFERENCES clients (id),
    number                VARCHAR(30),
    status                VARCHAR(20) NOT NULL
        CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'VOID')),
    currency              CHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    issue_date            DATE        NOT NULL,
    due_date              DATE,
    notes                 TEXT,
    terms                 TEXT,
    subtotal_minor        BIGINT      NOT NULL DEFAULT 0,
    tax_minor             BIGINT      NOT NULL DEFAULT 0,
    total_minor           BIGINT      NOT NULL DEFAULT 0,
    amount_paid_minor     BIGINT      NOT NULL DEFAULT 0 CHECK (amount_paid_minor >= 0),
    amount_credited_minor BIGINT      NOT NULL DEFAULT 0 CHECK (amount_credited_minor >= 0),
    -- client details frozen at issue
    bill_to_name          VARCHAR(200),
    bill_to_email         CITEXT,
    bill_to_phone         VARCHAR(50),
    bill_to_tax_id        VARCHAR(50),
    bill_to_address_line1 VARCHAR(200),
    bill_to_address_line2 VARCHAR(200),
    bill_to_city          VARCHAR(100),
    bill_to_region        VARCHAR(100),
    bill_to_postal_code   VARCHAR(20),
    bill_to_country       CHAR(2),
    -- seller details frozen at issue
    seller_name           VARCHAR(200),
    seller_email          CITEXT,
    seller_phone          VARCHAR(50),
    seller_tax_id         VARCHAR(50),
    seller_address_line1  VARCHAR(200),
    seller_address_line2  VARCHAR(200),
    seller_city           VARCHAR(100),
    seller_region         VARCHAR(100),
    seller_postal_code    VARCHAR(20),
    seller_country        CHAR(2),
    quote_id              UUID REFERENCES quotes (id),
    credited_invoice_id   UUID REFERENCES invoices (id),
    issued_at             TIMESTAMPTZ,
    voided_at             TIMESTAMPTZ,
    void_reason           TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoices_org_number UNIQUE (organization_id, number),
    CONSTRAINT ck_invoices_number_after_draft CHECK (status = 'DRAFT' OR number IS NOT NULL),
    CONSTRAINT ck_invoices_paid_within_total CHECK (amount_paid_minor + amount_credited_minor <= total_minor)
);
CREATE INDEX idx_invoices_org_status ON invoices (organization_id, status);
CREATE INDEX idx_invoices_org_client ON invoices (organization_id, client_id);
CREATE INDEX idx_invoices_org_issue_date ON invoices (organization_id, issue_date DESC);

ALTER TABLE quotes
    ADD CONSTRAINT fk_quotes_converted_invoice FOREIGN KEY (converted_invoice_id) REFERENCES invoices (id);

-- Line items. Price and tax rate are copied onto the line, so editing a product or a
-- tax rate later never changes an existing document.
CREATE TABLE quote_lines (
    id               UUID PRIMARY KEY,
    organization_id  UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    quote_id         UUID           NOT NULL REFERENCES quotes (id) ON DELETE CASCADE,
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
    -- Checked at commit: replacing a draft's lines deletes and re-inserts positions 1..n
    -- in one transaction, and Hibernate flushes the inserts before the deletes.
    CONSTRAINT uq_quote_lines_position UNIQUE (quote_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE invoice_lines (
    id               UUID PRIMARY KEY,
    organization_id  UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    invoice_id       UUID           NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
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
    CONSTRAINT uq_invoice_lines_position UNIQUE (invoice_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- Money received against an invoice. Online payments (Phase 5) also store the
-- provider's id; the unique constraint makes a replayed webhook a harmless no-op.
CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    organization_id     UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    invoice_id          UUID         NOT NULL REFERENCES invoices (id),
    amount_minor        BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency            CHAR(3)      NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    method              VARCHAR(20)  NOT NULL
        CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CHEQUE', 'CARD', 'ONLINE', 'OTHER')),
    paid_on             DATE         NOT NULL,
    reference           VARCHAR(200),
    provider            VARCHAR(30),
    provider_payment_id VARCHAR(200),
    recorded_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_provider_id UNIQUE (provider, provider_payment_id)
);
CREATE INDEX idx_payments_org_invoice ON payments (organization_id, invoice_id);
