package com.fatoora.invoicing.organization;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipIntegrationTest extends IntegrationTest {

    @Test
    void ownerAddsChangesAndRemovesAMember() throws Exception {
        Account owner = register("Team Co");
        Account colleague = register("Colleague's own org");

        UUID membershipId = addMember(owner, colleague, "MEMBER");
        getJson("/api/v1/members", owner.token()).andExpect(jsonPath("$", hasSize(2)));

        patchJson("/api/v1/members/" + membershipId, owner.token(), """
                {"role":"ADMIN"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        deleteJson("/api/v1/members/" + membershipId, owner.token()).andExpect(status().isNoContent());
        getJson("/api/v1/members", owner.token()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void memberCannotManageMembers() throws Exception {
        Account owner = register("Strict Co");
        Account member = register("Member's own org");
        Account outsider = register("Outsider org");
        addMember(owner, member, "MEMBER");
        String memberToken = switchTo(member, owner.orgId());

        postJson("/api/v1/members", memberToken, """
                {"email":"%s","role":"MEMBER"}""".formatted(outsider.email()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotGrantOwnerOrRemoveAnOwner() throws Exception {
        Account owner = register("Admin Co");
        Account admin = register("Admin's own org");
        Account outsider = register("Outsider org");
        addMember(owner, admin, "ADMIN");
        String adminToken = switchTo(admin, owner.orgId());
        UUID ownerMembershipId = membershipIdOf(owner, owner);

        postJson("/api/v1/members", adminToken, """
                {"email":"%s","role":"OWNER"}""".formatted(outsider.email()))
                .andExpect(status().isForbidden());
        deleteJson("/api/v1/members/" + ownerMembershipId, adminToken).andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerCannotLeaveOrBeDemoted() throws Exception {
        Account owner = register("Solo Co");
        UUID ownerMembershipId = membershipIdOf(owner, owner);

        patchJson("/api/v1/members/" + ownerMembershipId, owner.token(), """
                {"role":"MEMBER"}""")
                .andExpect(status().isConflict());
        deleteJson("/api/v1/members/" + ownerMembershipId, owner.token()).andExpect(status().isConflict());
    }

    @Test
    void addingAnUnknownEmailIs404AndAddingTwiceIs409() throws Exception {
        Account owner = register("Dup Co");
        Account colleague = register("Colleague org");

        postJson("/api/v1/members", owner.token(), """
                {"email":"%s","role":"MEMBER"}""".formatted(uniqueEmail()))
                .andExpect(status().isNotFound());

        addMember(owner, colleague, "MEMBER");
        postJson("/api/v1/members", owner.token(), """
                {"email":"%s","role":"MEMBER"}""".formatted(colleague.email()))
                .andExpect(status().isConflict());
    }

    private String switchTo(Account account, UUID orgId) throws Exception {
        return read(postJson("/api/v1/auth/switch-org/" + orgId, account.token(), null).andExpect(status().isOk()),
                "$.accessToken");
    }

    private UUID membershipIdOf(Account viewer, Account member) throws Exception {
        String json = getJson("/api/v1/members", viewer.token()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[?(@.userId == '" + member.userId() + "')].id");
        return UUID.fromString(ids.getFirst());
    }
}
