package com.fatoora.invoicing.common.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.IntegrationTest;
import com.fatoora.invoicing.organization.Role;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the central @TenantId mechanism that every future business table (clients,
 * invoices...) inherits from {@link TenantScopedEntity}.
 */
class TenantScopedEntityIntegrationTest extends IntegrationTest {

    @Autowired
    TenantProbeRepository probes;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    UUID orgA;
    UUID orgB;

    @BeforeEach
    void twoOrgs() throws Exception {
        orgA = register("Probe A").orgId();
        orgB = register("Probe B").orgId();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void insertsAreStampedWithTheCurrentOrganization() {
        UUID id = as(orgA, () -> probes.save(new TenantProbe("a-1")).getId());

        UUID storedOrg = jdbc.queryForObject(
                "select organization_id from test_tenant_probes where id = ?", UUID.class, id);
        assertThat(storedOrg).isEqualTo(orgA);
    }

    @Test
    void queriesOnlySeeTheCurrentOrganizationsRows() {
        UUID aId = as(orgA, () -> probes.save(new TenantProbe("a-only")).getId());
        as(orgB, () -> probes.save(new TenantProbe("b-only")));

        assertThat(as(orgB, () -> probes.findAll()))
                .extracting(TenantProbe::getLabel)
                .contains("b-only")
                .doesNotContain("a-only");
        assertThat(as(orgB, () -> probes.findById(aId))).isEmpty();
        assertThat(as(orgA, () -> probes.findById(aId))).isPresent();
    }

    @Test
    void withNoTenantNothingIsVisibleAndNothingCanBeInserted() {
        as(orgA, () -> probes.save(new TenantProbe("hidden")));

        List<TenantProbe> visible = tx.execute(s -> probes.findAll());
        assertThat(visible).isEmpty();
        assertThatThrownBy(() -> tx.execute(s -> probes.saveAndFlush(new TenantProbe("orphan"))))
                .isInstanceOf(Exception.class);
    }

    private <T> T as(UUID orgId, Supplier<T> work) {
        TenantContext.set(new CurrentTenant(orgId, UUID.randomUUID(), Role.OWNER));
        try {
            return tx.execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }
}
