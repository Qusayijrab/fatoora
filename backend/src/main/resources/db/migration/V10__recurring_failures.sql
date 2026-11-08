-- A recurring invoice that fails to generate (e.g. no exchange rate for its currency) used
-- to be retried every 15 minutes forever without telling anyone. Failures are now counted;
-- after 3 in a row the template pauses itself, its owners are emailed, and the reason is
-- shown in the app. Resuming a template paused this way retries the failed period.
ALTER TABLE recurring_templates
    ADD COLUMN failure_count     INT         NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    ADD COLUMN last_error        TEXT,
    ADD COLUMN last_failed_at    TIMESTAMPTZ,
    ADD COLUMN paused_at         TIMESTAMPTZ,
    ADD COLUMN paused_by_failure BOOLEAN     NOT NULL DEFAULT false;
