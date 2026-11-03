-- Test-only table. Real business tables (clients, invoices...) arrive in later phases;
-- this one lets Phase 1 prove that TenantScopedEntity filtering works end to end.
CREATE TABLE test_tenant_probes (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organizations (id),
    label           VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
