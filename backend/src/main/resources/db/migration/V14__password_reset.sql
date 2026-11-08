-- "Forgot password": a one-time link by email. 256 random bits, only the SHA-256 is
-- stored, valid for an hour, usable once. Using it signs out every session of the account.
CREATE TABLE password_reset_tokens (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
