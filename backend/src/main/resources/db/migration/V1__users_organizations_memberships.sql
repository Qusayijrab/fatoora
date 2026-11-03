-- Case-insensitive text: "Ali@x.com" and "ali@x.com" are the same email.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         CITEXT       NOT NULL UNIQUE,
    password_hash TEXT         NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE organizations (
    id               UUID PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    -- ISO 4217 code, e.g. USD, JOD. Default currency for invoices (Phase 7).
    default_currency CHAR(3)      NOT NULL CHECK (default_currency ~ '^[A-Z]{3}$'),
    -- IANA zone, e.g. Asia/Amman. Used for due dates and jobs (Phase 6).
    time_zone        VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Links users to organizations with a role. A user can belong to many orgs.
CREATE TABLE memberships (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_memberships_org_user UNIQUE (organization_id, user_id)
);

-- "Which orgs does this user belong to?" Lookups that start with organization_id
-- are already covered by the unique constraint's index.
CREATE INDEX idx_memberships_user_id ON memberships (user_id);
