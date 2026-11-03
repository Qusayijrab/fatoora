package com.fatoora.invoicing.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PortalIntegrationTest extends IntegrationTest {

    String token(Account a, String clientId) throws Exception {
        String url = read(postJson("/api/v1/clients/" + clientId + "/portal-link", a.token(), null).andExpect(status().isOk()), "$.url");
        assertThat(url).contains("/portal/");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    String issued(Account a, String client, String price) throws Exception {
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("Work", "1", price, null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        return id;
    }

    String sentQuote(Account a, String client) throws Exception {
        String id = read(postJson("/api/v1/quotes", a.token(), document(client, "JOD", line("Proposal", "1", "500", null))), "$.id");
        postJson("/api/v1/quotes/" + id + "/send", a.token(), null).andExpect(status().isOk());
        return id;
    }

    @Test
    void theClientSeesOnlyTheirOwnIssuedDocumentsAndCanPay() throws Exception {
        Account a = register("Portal Co");
        String mine = createClient(a, "Portal Client");
        String other = createClient(a, "Someone Else");
        issued(a, mine, "100");
        issued(a, mine, "40.500");
        issued(a, other, "999");
        postJson("/api/v1/invoices", a.token(), document(mine, "JOD", line("draft", "1", "1", null))); // drafts stay private
        sentQuote(a, mine);

        getJson("/api/v1/public/portal/" + token(a, mine), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName").value("Portal Co"))
                .andExpect(jsonPath("$.clientName").value("Portal Client"))
                .andExpect(jsonPath("$.invoices", hasSize(2)))
                .andExpect(jsonPath("$.invoices[0].payUrl").value(org.hamcrest.Matchers.containsString("/pay/")))
                .andExpect(jsonPath("$.outstanding[0].amount").value("140.500"))
                .andExpect(jsonPath("$.quotes", hasSize(1)));
    }

    @Test
    void theClientAnswersAQuoteOnceAndOnlyTheirOwn() throws Exception {
        Account a = register("Answer Co");
        String mine = createClient(a, "Mine");
        String other = createClient(a, "Other");
        String myQuote = sentQuote(a, mine);
        String otherQuote = sentQuote(a, other);
        String t = token(a, mine);

        postJson("/api/v1/public/portal/" + t + "/quotes/" + otherQuote + "/accept", null, null).andExpect(status().isNotFound());
        postJson("/api/v1/public/portal/" + t + "/quotes/" + myQuote + "/accept", null, null).andExpect(jsonPath("$.status").value("ACCEPTED"));
        postJson("/api/v1/public/portal/" + t + "/quotes/" + myQuote + "/reject", null, null).andExpect(status().isConflict());
        getJson("/api/v1/quotes/" + myQuote, a.token()).andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void pdfsAreLimitedToTheClientsOwnDocuments() throws Exception {
        Account a = register("Portal Pdf Co");
        String mine = createClient(a, "Mine");
        String other = createClient(a, "Other");
        String myInvoice = issued(a, mine, "10");
        String otherInvoice = issued(a, other, "10");
        String t = token(a, mine);

        getJson("/api/v1/public/portal/" + t + "/invoices/" + myInvoice + "/pdf", null)
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        getJson("/api/v1/public/portal/" + t + "/invoices/" + otherInvoice + "/pdf", null).andExpect(status().isNotFound());
    }

    @Test
    void revokingALinkKillsItAndTheNextOneIsDifferent() throws Exception {
        Account a = register("Revoke Co");
        String client = createClient(a, "C");
        String first = token(a, client);
        assertThat(token(a, client)).isEqualTo(first); // stable until revoked

        deleteJson("/api/v1/clients/" + client + "/portal-link", a.token()).andExpect(status().isNoContent());
        getJson("/api/v1/public/portal/" + first, null).andExpect(status().isNotFound());
        assertThat(token(a, client)).isNotEqualTo(first);

        // Another organization cannot mint a link for my client.
        Account b = register("Other Revoke Co");
        postJson("/api/v1/clients/" + client + "/portal-link", b.token(), null).andExpect(status().isNotFound());
        // A pay token is not a portal token.
        getJson("/api/v1/public/portal/" + "x".repeat(43), null).andExpect(status().isNotFound());
    }

    @Test
    void auditLogIsForAdminsButADocumentsHistoryIsForEveryMember() throws Exception {
        Account owner = register("Audit View Co");
        String client = createClient(owner, "C");
        String invoice = issued(owner, client, "5");

        getJson("/api/v1/audit-events", owner.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorName").value("Test User"));

        Account member = register("Audit Member Org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        getJson("/api/v1/audit-events", memberToken).andExpect(status().isForbidden());
        getJson("/api/v1/audit-events?entityId=" + invoice, memberToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("issued"))
                .andExpect(jsonPath("$.total").value(2));
    }
}
