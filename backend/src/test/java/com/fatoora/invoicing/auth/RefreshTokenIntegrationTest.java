package com.fatoora.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** Sessions outlive the 1-hour access token, and a copied refresh token is caught. */
class RefreshTokenIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    String login(Account a) throws Exception {
        return read(postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s"}""".formatted(a.email(), PASSWORD))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshExpiresIn").value(30 * 24 * 3600)), "$.refreshToken");
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", null, """
                {"refreshToken":"%s"}""".formatted(refreshToken));
    }

    /** Pretends the token was rotated longer ago than the grace window for parallel requests. */
    void usedLongAgo(String refreshToken) {
        jdbc.update("update refresh_tokens set used_at = now() - interval '5 minutes' where token_hash = ?",
                EmailVerificationService.hash(refreshToken));
    }

    @Test
    void aRefreshTokenBuysANewAccessTokenAndIsReplaced() throws Exception {
        Account a = register("Long Session Co");
        String first = login(a);

        String body = refresh(first).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        String second = com.jayway.jsonpath.JsonPath.read(body, "$.refreshToken");
        assertThat(second).isNotEqualTo(first);
        getJson("/api/v1/me", access)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.id").value(a.orgId().toString()));
        // Only hashes are stored.
        Integer plain = jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, first);
        assertThat(plain).isZero();
    }

    @Test
    void aReplacedTokenUsedAgainLaterRevokesTheWholeSession() throws Exception {
        Account a = register("Stolen Token Co");
        String first = login(a);
        String second = read(refresh(first).andExpect(status().isOk()), "$.refreshToken");

        // A thief replays the old token after the owner already rotated it.
        usedLongAgo(first);
        refresh(first).andExpect(status().isUnauthorized());
        // ...which ends the owner's current token too: both must sign in again.
        refresh(second).andExpect(status().isUnauthorized());
        // Other sessions of the same user (another device) are untouched.
        refresh(login(a)).andExpect(status().isOk());
    }

    @Test
    void parallelRequestsRacingWithTheSameTokenDoNotLogTheUserOut() throws Exception {
        Account a = register("Busy Tabs Co");
        String token = login(a);
        String fromTabOne = read(refresh(token).andExpect(status().isOk()), "$.refreshToken");
        // A second request with the same token, seconds later: within the grace window.
        String fromTabTwo = read(refresh(token).andExpect(status().isOk()), "$.refreshToken");
        refresh(fromTabOne).andExpect(status().isOk());
        refresh(fromTabTwo).andExpect(status().isOk());
    }

    @Test
    void signingOutEndsTheSessionAndExpiredOrUnknownTokensAreRefused() throws Exception {
        Account a = register("Logout Co");
        String token = login(a);
        postJson("/api/v1/auth/logout", null, """
                {"refreshToken":"%s"}""".formatted(token)).andExpect(status().isNoContent());
        refresh(token).andExpect(status().isUnauthorized());

        String other = login(a);
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                EmailVerificationService.hash(other));
        refresh(other).andExpect(status().isUnauthorized());
        refresh("not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void someoneRemovedFromAnOrganizationCannotRefreshIntoIt() throws Exception {
        Account owner = register("Removing Co");
        Account member = register("Member Home Co");
        String membershipId = addMember(owner, member, "MEMBER").toString();
        String intoOwnersOrg = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null)
                .andExpect(status().isOk()), "$.refreshToken");

        deleteJson("/api/v1/members/" + membershipId, owner.token()).andExpect(status().is2xxSuccessful());
        refresh(intoOwnersOrg).andExpect(status().isUnauthorized());
    }
}
