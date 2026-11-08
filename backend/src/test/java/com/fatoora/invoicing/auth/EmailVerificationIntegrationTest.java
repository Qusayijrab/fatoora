package com.fatoora.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/** The real flow: sign up, can't email clients, open the link from the email, now you can. */
class EmailVerificationIntegrationTest extends IntegrationTest {

    @Value("${test.mailpit.api}")
    String mailpitApi;

    @Autowired
    JdbcTemplate jdbc;

    /** Signs up without the base helper (which marks the address confirmed). Returns the token. */
    String signUp(String email, String org) throws Exception {
        return read(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s","fullName":"New Owner","organizationName":"%s",
                 "defaultCurrency":"JOD","timeZone":"Asia/Amman"}""".formatted(email, PASSWORD, org)))
                .andExpect(status().isCreated()), "$.accessToken");
    }

    /** The newest confirmation link sent to {@code email}, read from Mailpit. */
    String linkTokenFor(String email) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        String search = http.send(HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/search?query="
                + URLEncoder.encode("to:" + email + " subject:\"Confirm your email\"", StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        List<String> ids = JsonPath.read(search, "$.messages[*].ID");
        assertThat(ids).isNotEmpty();
        String message = http.send(HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/message/" + ids.getFirst())).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("/verify-email/([A-Za-z0-9_-]{43})").matcher((String) JsonPath.read(message, "$.HTML"));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    String issuedInvoice(String token) throws Exception {
        String client = read(postJson("/api/v1/clients", token, """
                {"name":"Client","email":"client@example.com","country":"JO"}"""), "$.id");
        String id = read(postJson("/api/v1/invoices", token, document(client, "JOD", line("Work", "1", "10", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", token, null).andExpect(status().isOk());
        return id;
    }

    @Test
    void anOrganizationCanOnlyEmailClientsOnceItsOwnerConfirmedTheirAddress() throws Exception {
        String email = uniqueEmail();
        String token = signUp(email, "Fresh Co");
        getJson("/api/v1/me", token).andExpect(jsonPath("$.emailVerified").value(false));

        String invoice = issuedInvoice(token);
        postJson("/api/v1/invoices/" + invoice + "/email", token, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Confirm your email")));

        String link = linkTokenFor(email);
        // Only a hash is stored: the token itself is nowhere in the database.
        Integer plain = jdbc.queryForObject("select count(*) from email_verification_tokens where token_hash = ?",
                Integer.class, link);
        assertThat(plain).isZero();

        postJson("/api/v1/auth/verify-email", null, """
                {"token":"%s"}""".formatted(link)).andExpect(status().isNoContent());
        getJson("/api/v1/me", token).andExpect(jsonPath("$.emailVerified").value(true));
        postJson("/api/v1/invoices/" + invoice + "/email", token, "{}").andExpect(status().isOk());

        // Each link works once; a made-up one never does.
        postJson("/api/v1/auth/verify-email", null, """
                {"token":"%s"}""".formatted(link)).andExpect(status().isBadRequest());
        postJson("/api/v1/auth/verify-email", null, """
                {"token":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}""").andExpect(status().isBadRequest());
        // Nothing left to confirm.
        postJson("/api/v1/me/verification-email", token, null).andExpect(status().isConflict());
    }

    @Test
    void expiredLinksAreRefusedAndANewOneCanBeRequested() throws Exception {
        String email = uniqueEmail();
        String token = signUp(email, "Late Co");
        String old = linkTokenFor(email);
        jdbc.update("update email_verification_tokens set expires_at = now() - interval '1 minute' where user_id = "
                + "(select id from users where email = ?)", email);
        postJson("/api/v1/auth/verify-email", null, """
                {"token":"%s"}""".formatted(old)).andExpect(status().isBadRequest());

        postJson("/api/v1/me/verification-email", token, null).andExpect(status().isNoContent());
        String fresh = linkTokenFor(email);
        assertThat(fresh).isNotEqualTo(old);
        postJson("/api/v1/auth/verify-email", null, """
                {"token":"%s"}""".formatted(fresh)).andExpect(status().isNoContent());
        getJson("/api/v1/me", token).andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void resendingIsLimited() throws Exception {
        String token = signUp(uniqueEmail(), "Impatient Co");
        for (int i = 0; i < 3; i++) {
            postJson("/api/v1/me/verification-email", token, null).andExpect(status().isNoContent());
        }
        postJson("/api/v1/me/verification-email", token, null).andExpect(status().isTooManyRequests());
    }
}
