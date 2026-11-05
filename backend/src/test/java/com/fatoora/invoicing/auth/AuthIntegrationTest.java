package com.fatoora.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerCreatesUserOrganizationAndOwnerMembership() throws Exception {
        Account account = register("Acme Ltd");

        getJson("/api/v1/me", account.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()))
                .andExpect(jsonPath("$.organization.name").value("Acme Ltd"))
                .andExpect(jsonPath("$.organization.defaultCurrency").value("JOD"))
                .andExpect(jsonPath("$.organization.timeZone").value("Asia/Amman"))
                .andExpect(jsonPath("$.organization.role").value("OWNER"));

        getJson("/api/v1/members", account.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void loginReturnsTokenForTheUsersOrganization() throws Exception {
        Account account = register("Login Co");

        String token = read(
                postJson("/api/v1/auth/login", null, loginBody(account.email(), PASSWORD)).andExpect(status().isOk()),
                "$.accessToken");

        getJson("/api/v1/organizations/current", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.orgId().toString()));
    }

    @Test
    void loginIgnoresEmailCase() throws Exception {
        Account account = register("Case Co");

        postJson("/api/v1/auth/login", null, loginBody(account.email().toUpperCase(), PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSame401() throws Exception {
        Account account = register("Secret Co");

        String wrongPassword = postJson("/api/v1/auth/login", null, loginBody(account.email(), "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = postJson("/api/v1/auth/login", null, loginBody(uniqueEmail(), "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).contains("Invalid email or password");
        assertThat(unknownEmail).isEqualTo(wrongPassword.replace(account.email(), ""));
    }

    @Test
    void duplicateEmailIs409ProblemJson() throws Exception {
        Account account = register("First Co");

        postJson("/api/v1/auth/register", null, registerBody(account.email().toUpperCase(), "Second Co", "USD", "UTC"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    void invalidRegistrationListsFieldErrors() throws Exception {
        String body = """
                {"email":"not-an-email","password":"short","fullName":"","organizationName":"X",
                 "defaultCurrency":"usd","timeZone":"+03:00"}""";

        postJson("/api/v1/auth/register", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.defaultCurrency").exists())
                .andExpect(jsonPath("$.errors.timeZone").exists());
    }

    /** BCrypt's limit is 72 bytes, not characters: an Arabic letter is 2 bytes in UTF-8. */
    @Test
    void passwordLengthIsMeasuredInBytesSoLongArabicPasswordsGetA400NotA500() throws Exception {
        String arabic40 = "كلمةسرطويلةجدا".repeat(3).substring(0, 40); // 40 letters = 80 bytes
        String body = """
                {"email":"%s","password":"%s","fullName":"Someone","organizationName":"Arabic Co",
                 "defaultCurrency":"JOD","timeZone":"Asia/Amman"}""".formatted(uniqueEmail(), arabic40);
        postJson("/api/v1/auth/register", null, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(containsString("72 bytes")));
        postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s"}""".formatted(uniqueEmail(), arabic40))
                .andExpect(status().isBadRequest());

        // 36 Arabic letters = 72 bytes: exactly at the limit, accepted, and it logs in.
        String arabic36 = arabic40.substring(0, 36);
        String email = uniqueEmail();
        postJson("/api/v1/auth/register", null, """
                {"email":"%s","password":"%s","fullName":"Someone","organizationName":"Arabic Co",
                 "defaultCurrency":"JOD","timeZone":"Asia/Amman"}""".formatted(email, arabic36))
                .andExpect(status().isCreated());
        postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s"}""".formatted(email, arabic36))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenIs401ProblemJson() throws Exception {
        getJson("/api/v1/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        getJson("/api/v1/me", "not.a.jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void userCanCreateASecondOrganizationAndSwitchToIt() throws Exception {
        Account account = register("Main Co");

        String newOrgId = read(
                postJson("/api/v1/organizations", account.token(),
                                """
                                {"name":"Side Co","defaultCurrency":"USD","timeZone":"UTC"}""")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.role").value("OWNER")),
                "$.id");

        getJson("/api/v1/organizations", account.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        String switched = read(
                postJson("/api/v1/auth/switch-org/" + newOrgId, account.token(), null).andExpect(status().isOk()),
                "$.accessToken");

        getJson("/api/v1/organizations/current", switched)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Side Co"));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        getJson("/actuator/health", null).andExpect(status().isOk());
        getJson("/v3/api-docs", null).andExpect(status().isOk());
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private static String registerBody(String email, String org, String currency, String zone) {
        return """
                {"email":"%s","password":"%s","fullName":"Someone","organizationName":"%s",
                 "defaultCurrency":"%s","timeZone":"%s"}""".formatted(email, PASSWORD, org, currency, zone);
    }
}
