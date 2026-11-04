package com.fatoora.invoicing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for integration tests: full Spring app + real Postgres (Testcontainers),
 * called through MockMvc exactly like an HTTP client would.
 *
 * <p>Tests don't clean the database between runs. Each test registers users with
 * unique random emails instead, so tests can't interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    /** Invoices are issued dated today in the organization's zone (tests register with Asia/Amman). */
    protected static LocalDate orgToday() {
        return LocalDate.now(ZoneId.of("Asia/Amman"));
    }

    /** The year in invoice numbers issued now. */
    protected static final String YEAR = String.valueOf(orgToday().getYear());

    @Autowired
    protected MockMvc mvc;

    /** A registered user, logged in to their own new organization. */
    protected record Account(String email, String token, UUID userId, UUID orgId) {}

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected Account register(String organizationName) throws Exception {
        return register(uniqueEmail(), organizationName);
    }

    protected Account register(String email, String organizationName) throws Exception {
        String body = """
                {"email":"%s","password":"%s","fullName":"Test User","organizationName":"%s",
                 "defaultCurrency":"JOD","timeZone":"Asia/Amman"}
                """.formatted(email, PASSWORD, organizationName);
        String token = read(perform(post("/api/v1/auth/register"), null, body).andExpect(status().isCreated()), "$.accessToken");
        String me = perform(get("/api/v1/me"), token, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(
                email,
                token,
                UUID.fromString(JsonPath.read(me, "$.id")),
                UUID.fromString(JsonPath.read(me, "$.organization.id")));
    }

    /** Adds {@code member} to the org of {@code admin}'s token; returns the new membership id. */
    protected UUID addMember(Account admin, Account member, String role) throws Exception {
        String body = """
                {"email":"%s","role":"%s"}""".formatted(member.email(), role);
        return UUID.fromString(read(postJson("/api/v1/members", admin.token(), body).andExpect(status().isCreated()), "$.id"));
    }

    /** Creates a client in the account's organization and returns its id. */
    protected String createClient(Account account, String name) throws Exception {
        return read(postJson("/api/v1/clients", account.token(), """
                {"name":"%s","email":"billing@example.com","taxId":"TX-1","city":"Amman","country":"JO"}"""
                .formatted(name)).andExpect(status().isCreated()), "$.id");
    }

    /** One line: description, quantity, unit price, optional tax rate id. */
    protected static String line(String description, String quantity, String unitPrice, String taxRateId) {
        return """
                {"description":"%s","quantity":"%s","unitPrice":"%s","taxRateId":%s}"""
                .formatted(description, quantity, unitPrice, taxRateId == null ? "null" : "\"" + taxRateId + "\"");
    }

    protected static String document(String clientId, String currency, String... lines) {
        return """
                {"clientId":"%s","currency":"%s","issueDate":"2026-11-03","lines":[%s]}"""
                .formatted(clientId, currency, String.join(",", lines));
    }

    protected ResultActions getJson(String path, String token) throws Exception {
        return perform(get(path), token, null);
    }

    protected ResultActions postJson(String path, String token, String body) throws Exception {
        return perform(post(path), token, body);
    }

    protected ResultActions patchJson(String path, String token, String body) throws Exception {
        return perform(patch(path), token, body);
    }

    protected ResultActions putJson(String path, String token, String body) throws Exception {
        return perform(put(path), token, body);
    }

    protected ResultActions deleteJson(String path, String token) throws Exception {
        return perform(delete(path), token, null);
    }

    protected static String read(ResultActions result, String jsonPath) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath).toString();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }
}
