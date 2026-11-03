package com.fatoora.invoicing.quote;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;

class QuoteIntegrationTest extends IntegrationTest {

    String draftQuote(Account a, String clientId) throws Exception {
        return read(postJson("/api/v1/quotes", a.token(), document(clientId, "USD",
                        line("Website redesign", "1", "2400", null),
                        line("Hosting (months)", "12", "15.50", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total.amount").value("2586.00"))
                .andExpect(jsonPath("$.validUntil").value("2026-12-03")), "$.id");
    }

    @Test
    void sendAcceptConvert() throws Exception {
        Account a = register("Quote Co");
        String client = createClient(a, "Wadi Rum Tours");
        String id = draftQuote(a, client);

        postJson("/api/v1/quotes/" + id + "/send", a.token(), null)
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.number").value("QUO-2026-0001"))
                .andExpect(jsonPath("$.billTo.name").value("Wadi Rum Tours"));

        putJson("/api/v1/quotes/" + id, a.token(), document(client, "USD", line("x", "1", "1", null)))
                .andExpect(status().isConflict());

        postJson("/api/v1/quotes/" + id + "/accept", a.token(), null).andExpect(jsonPath("$.status").value("ACCEPTED"));

        String invoiceId = read(postJson("/api/v1/quotes/" + id + "/convert", a.token(), null)
                .andExpect(status().isCreated()), "$.invoiceId");
        getJson("/api/v1/invoices/" + invoiceId, a.token())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.quoteId").value(id))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.total.amount").value("2586.00"));
        getJson("/api/v1/quotes/" + id, a.token()).andExpect(jsonPath("$.convertedInvoiceId").value(invoiceId));

        // Converting twice is refused.
        postJson("/api/v1/quotes/" + id + "/convert", a.token(), null).andExpect(status().isConflict());
    }

    @Test
    void illegalTransitionsAreRefused() throws Exception {
        Account a = register("Strict Quotes");
        String id = draftQuote(a, createClient(a, "C"));

        postJson("/api/v1/quotes/" + id + "/accept", a.token(), null).andExpect(status().isConflict());
        postJson("/api/v1/quotes/" + id + "/convert", a.token(), null).andExpect(status().isConflict());

        postJson("/api/v1/quotes/" + id + "/send", a.token(), null);
        postJson("/api/v1/quotes/" + id + "/reject", a.token(), null).andExpect(jsonPath("$.status").value("REJECTED"));
        postJson("/api/v1/quotes/" + id + "/accept", a.token(), null).andExpect(status().isConflict());
        postJson("/api/v1/quotes/" + id + "/convert", a.token(), null).andExpect(status().isConflict());
        deleteJson("/api/v1/quotes/" + id, a.token()).andExpect(status().isConflict());
    }

    @Test
    void anotherOrganizationCannotSeeMyQuotes() throws Exception {
        Account a = register("Quote A");
        Account b = register("Quote B");
        String id = draftQuote(a, createClient(a, "C"));

        getJson("/api/v1/quotes/" + id, b.token()).andExpect(status().isNotFound());
        postJson("/api/v1/quotes/" + id + "/send", b.token(), null).andExpect(status().isNotFound());
        getJson("/api/v1/quotes", b.token()).andExpect(jsonPath("$.total").value(0));
    }
}
