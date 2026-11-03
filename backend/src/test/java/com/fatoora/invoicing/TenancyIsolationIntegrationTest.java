package com.fatoora.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CLAUDE.md rule 2: org A must never read or change org B's data, and the org id is
 * never trusted from the client. Each test sets up two separate companies.
 */
class TenancyIsolationIntegrationTest extends IntegrationTest {

    Account alice; // owner of org A
    Account bob; // owner of org B

    @BeforeEach
    void twoSeparateCompanies() throws Exception {
        alice = register("Org A");
        bob = register("Org B");
    }

    @Test
    void membersListOnlyShowsTheCallersOrganization() throws Exception {
        getJson("/api/v1/members", alice.token())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(alice.userId().toString()));
    }

    @Test
    void cannotChangeOrRemoveAnotherOrgsMembership() throws Exception {
        String bobsMembershipId = read(getJson("/api/v1/members", bob.token()), "$[0].id");

        patchJson("/api/v1/members/" + bobsMembershipId, alice.token(), """
                {"role":"MEMBER"}""")
                .andExpect(status().isNotFound());
        deleteJson("/api/v1/members/" + bobsMembershipId, alice.token()).andExpect(status().isNotFound());

        // Bob is untouched.
        getJson("/api/v1/members", bob.token())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void cannotSwitchIntoAnOrgYouDontBelongTo() throws Exception {
        postJson("/api/v1/auth/switch-org/" + bob.orgId(), alice.token(), null).andExpect(status().isForbidden());
    }

    @Test
    void switchingToANonExistentOrgLooksTheSameAsAForeignOrg() throws Exception {
        postJson("/api/v1/auth/switch-org/" + UUID.randomUUID(), alice.token(), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationIdInTheRequestBodyIsIgnored() throws Exception {
        Account carol = register("Org C");

        // Alice tries to smuggle Bob's org id into the request body.
        postJson("/api/v1/members", alice.token(), """
                {"email":"%s","role":"MEMBER","organizationId":"%s"}""".formatted(carol.email(), bob.orgId()))
                .andExpect(status().isCreated());

        getJson("/api/v1/members", alice.token()).andExpect(jsonPath("$", hasSize(2)));
        getJson("/api/v1/members", bob.token()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void organizationsListOnlyShowsTheCallersMemberships() throws Exception {
        String body = getJson("/api/v1/organizations", alice.token())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(bob.orgId().toString());
    }

    @Test
    void removedMemberLosesAccessImmediatelyEvenWithAValidToken() throws Exception {
        UUID membershipId = addMember(bob, alice, "ADMIN");
        String aliceInB = read(
                postJson("/api/v1/auth/switch-org/" + bob.orgId(), alice.token(), null).andExpect(status().isOk()),
                "$.accessToken");
        getJson("/api/v1/members", aliceInB).andExpect(status().isOk());

        deleteJson("/api/v1/members/" + membershipId, bob.token()).andExpect(status().isNoContent());

        // Same, still-unexpired token: now rejected because the membership is gone.
        getJson("/api/v1/members", aliceInB).andExpect(status().isForbidden());
        getJson("/api/v1/organizations/current", aliceInB).andExpect(status().isForbidden());
        // Alice can still see her own orgs and switch back.
        getJson("/api/v1/organizations", aliceInB).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void roleChangesApplyOnTheNextRequest() throws Exception {
        UUID membershipId = addMember(bob, alice, "ADMIN");
        String aliceInB = read(postJson("/api/v1/auth/switch-org/" + bob.orgId(), alice.token(), null), "$.accessToken");
        Account dave = register("Org D");

        patchJson("/api/v1/members/" + membershipId, bob.token(), """
                {"role":"MEMBER"}""").andExpect(status().isOk());

        // Alice's token was issued while she was ADMIN, but the role is read fresh.
        postJson("/api/v1/members", aliceInB, """
                {"email":"%s","role":"MEMBER"}""".formatted(dave.email()))
                .andExpect(status().isForbidden());
    }
}
