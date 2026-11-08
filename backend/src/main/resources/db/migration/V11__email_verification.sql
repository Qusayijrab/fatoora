-- New accounts confirm their email address before the organization can email clients
-- from our sender address. Accounts created before this check count as verified.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = created_at;

-- One-time links: 256 random bits, only the SHA-256 is stored (a database leak can't
-- verify anyone). Expire after a few days; used_at makes each usable once.
CREATE TABLE email_verification_tokens (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);
