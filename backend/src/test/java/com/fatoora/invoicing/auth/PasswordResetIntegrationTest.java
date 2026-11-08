package com.fatoora.invoicing.auth;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** "Forgot password", end to end through the real email (Mailpit). */
class PasswordResetIntegrationTest extends IntegrationTest {

    static final String NEW_PASSWORD = "a-brand-new-passphrase";

    @Value("${test.mailpit.api}")
    String mailpitApi;

    @Autowired
    JdbcTemplate jdbc;

    ResultActions forgot(String email) throws Exception {
        return postJson("/api/v1/auth/forgot-password", null, """
                {"email":"%s"}""".formatted(email));
    }

    ResultActions reset(String token, String password) throws Exception {
        return postJson("/api/v1/auth/reset-password", null, """
                {"token":"%s","password":"%s"}""".formatted(token, password));
    }

    ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", null, """
                {"email":"%s","password":"%s"}""".formatted(email, password));
    }

    List<String> resetEmailsTo(String email) throws Exception {
        String search = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/search?query="
                + URLEncoder.encode("to:" + email + " subject:\"Reset your Fatoora password\"", StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        return JsonPath.read(search, "$.messages[*].ID");
    }

    /** The token from the newest reset email to {@code email}. */
    String linkTokenFor(String email) throws Exception {
        List<String> ids = resetEmailsTo(email);
        assertThat(ids).isNotEmpty();
        String message = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/message/"
                + ids.getFirst())).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = Pattern.compile("/reset-password/([A-Za-z0-9_-]{43})").matcher((String) JsonPath.read(message, "$.HTML"));
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void aLinkByEmailSetsANewPasswordOnceAndSignsOutEverywhere() throws Exception {
        Account a = register("Forgetful Co");
        String refresh = read(login(a.email(), PASSWORD).andExpect(status().isOk()), "$.refreshToken");

        forgot(a.email().toUpperCase()).andExpect(status().isNoContent());
        String token = linkTokenFor(a.email());
        // Only a hash is stored.
        Integer plain = jdbc.queryForObject("select count(*) from password_reset_tokens where token_hash = ?", Integer.class, token);
        assertThat(plain).isZero();

        reset(token, "short").andExpect(status().isBadRequest());
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        login(a.email(), PASSWORD).andExpect(status().isUnauthorized());
        login(a.email(), NEW_PASSWORD).andExpect(status().isOk());
        // Sessions from before the reset are gone.
        postJson("/api/v1/auth/refresh", null, """
                {"refreshToken":"%s"}""".formatted(refresh)).andExpect(status().isUnauthorized());
        // The link works once.
        reset(token, "yet-another-passphrase").andExpect(status().isBadRequest());
    }

    @Test
    void usingOneLinkKillsTheOthersAndExpiredLinksAreRefused() throws Exception {
        Account a = register("Two Links Co");
        forgot(a.email()).andExpect(status().isNoContent());
        String first = linkTokenFor(a.email());
        forgot(a.email()).andExpect(status().isNoContent());
        String second = linkTokenFor(a.email());
        assertThat(second).isNotEqualTo(first);

        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(first, "another-new-passphrase").andExpect(status().isBadRequest());

        Account b = register("Late Link Co");
        forgot(b.email()).andExpect(status().isNoContent());
        String late = linkTokenFor(b.email());
        jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 second' where token_hash = ?",
                EmailVerificationService.hash(late));
        reset(late, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(b.email(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownAddressesGetTheSameAnswerAndNoEmail() throws Exception {
        String nobody = uniqueEmail();
        forgot(nobody).andExpect(status().isNoContent());
        assertThat(resetEmailsTo(nobody)).isEmpty();
        forgot("not-an-email").andExpect(status().isBadRequest());
    }

    @Test
    void oneAddressCantBeFloodedWithResetEmails() throws Exception {
        Account a = register("Flooded Co");
        for (int i = 0; i < 5; i++) {
            forgot(a.email()).andExpect(status().isNoContent()); // same answer every time
        }
        assertThat(resetEmailsTo(a.email())).hasSize(3);
    }
}
