-- Sessions outlive the 1-hour access token: the client trades a refresh token for a new
-- pair. Only the SHA-256 is stored. Every use rotates it (used_at set, a new row in the
-- same family); presenting an already-used token again (outside a short grace window for
-- parallel requests) revokes the whole family, which logs a thief and the owner out.
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    token_hash      CHAR(64)    NOT NULL UNIQUE,
    family_id       UUID        NOT NULL,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id UUID REFERENCES organizations (id) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
