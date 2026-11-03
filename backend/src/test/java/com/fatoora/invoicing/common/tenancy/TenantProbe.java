package com.fatoora.invoicing.common.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Test-only tenant-scoped entity, standing in for future clients/invoices. */
@Entity
@Table(name = "test_tenant_probes")
public class TenantProbe extends TenantScopedEntity {

    @Column(nullable = false)
    private String label;

    protected TenantProbe() {}

    public TenantProbe(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
