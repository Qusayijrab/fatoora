package com.fatoora.invoicing.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditAndSettingsIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void creatingThingsWritesAuditEventsWithTheActor() throws Exception {
        Account a = register("Audit Co");
        String clientId = read(postJson("/api/v1/clients", a.token(), """
                {"name":"Audited Client"}"""), "$.id");

        List<String> actions = jdbc.queryForList(
                "select entity_type || '.' || action from audit_events where organization_id = ? order by created_at",
                String.class, a.orgId());
        assertThat(actions).contains("organization.created", "client.created");

        UUID actor = jdbc.queryForObject(
                "select actor_user_id from audit_events where entity_id = ?", UUID.class, UUID.fromString(clientId));
        assertThat(actor).isEqualTo(a.userId());
    }

    @Test
    void auditEventsCannotBeChangedOrDeleted() throws Exception {
        Account a = register("Immutable Audit");
        assertThatThrownBy(() -> jdbc.update("update audit_events set summary = 'tampered' where organization_id = ?", a.orgId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_events where organization_id = ?", a.orgId()))
                .hasMessageContaining("append-only");
    }

    @Test
    void ownerUpdatesSellerDetailsAndMembersCannot() throws Exception {
        Account owner = register("Settings Co");
        String body = """
                {"name":"Settings Co","defaultCurrency":"JOD","timeZone":"Asia/Amman","legalName":"Settings Co LLC",
                 "taxId":"0123456","email":"Billing@Settings.co","addressLine1":"Rainbow St 12","city":"Amman","country":"JO"}""";

        patchJson("/api/v1/organizations/current", owner.token(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Settings Co LLC"))
                .andExpect(jsonPath("$.email").value("billing@settings.co"));
        getJson("/api/v1/organizations/current", owner.token()).andExpect(jsonPath("$.taxId").value("0123456"));

        Account member = register("Member org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        patchJson("/api/v1/organizations/current", memberToken, body).andExpect(status().isForbidden());
    }

    @Test
    void dashboardSummaryCountsOnlyMyOrganization() throws Exception {
        Account a = register("Summary A");
        Account b = register("Summary B");
        postJson("/api/v1/clients", a.token(), """
                {"name":"Only in A"}""").andExpect(status().isCreated());

        getJson("/api/v1/dashboard/summary", a.token()).andExpect(jsonPath("$.clients").value(1));
        getJson("/api/v1/dashboard/summary", b.token()).andExpect(jsonPath("$.clients").value(0));
    }
}
