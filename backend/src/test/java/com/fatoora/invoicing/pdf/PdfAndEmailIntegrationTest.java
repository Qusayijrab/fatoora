package com.fatoora.invoicing.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import com.fatoora.invoicing.storage.DocumentStorage;
import com.fatoora.invoicing.storage.StorageConfig.InMemoryDocumentStorage;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

class PdfAndEmailIntegrationTest extends IntegrationTest {

    @Autowired
    DocumentStorage storage;

    @Value("${test.mailpit.api}")
    String mailpitApi;

    String issuedInvoice(Account a, String clientName) throws Exception {
        String client = createClient(a, clientName);
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("Consulting", "3", "40.500", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        return id;
    }

    @Test
    void issuedInvoicePdfIsRenderedOnceThenServedFromStorage() throws Exception {
        Account a = register("Pdf Co");
        String id = issuedInvoice(a, "شركة الندى للتجارة");
        int before = ((InMemoryDocumentStorage) storage).size();

        byte[] first = getJson("/api/v1/invoices/" + id + "/pdf", a.token())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("INV-" + YEAR + "-0001.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] second = getJson("/api/v1/invoices/" + id + "/pdf", a.token()).andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(first, 0, 5)).isEqualTo("%PDF-");
        assertThat(second).isEqualTo(first);
        assertThat(((InMemoryDocumentStorage) storage).size()).isEqualTo(before + 1);

        // A payment changes the state, so a new PDF (with the new balance) is stored.
        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"10","method":"CASH"}""");
        getJson("/api/v1/invoices/" + id + "/pdf", a.token()).andExpect(status().isOk());
        assertThat(((InMemoryDocumentStorage) storage).size()).isEqualTo(before + 2);
    }

    @Test
    void draftPdfIsRenderedButNotStored() throws Exception {
        Account a = register("Draft Pdf Co");
        String client = createClient(a, "C");
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "USD", line("x", "1", "5", null))), "$.id");
        int before = ((InMemoryDocumentStorage) storage).size();

        getJson("/api/v1/invoices/" + id + "/pdf", a.token())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", Matchers.containsString("invoice-draft.pdf")));
        assertThat(((InMemoryDocumentStorage) storage).size()).isEqualTo(before);
    }

    @Test
    void anotherOrganizationCannotDownloadOrEmailMyInvoice() throws Exception {
        Account a = register("Pdf Private A");
        Account b = register("Pdf Private B");
        String id = issuedInvoice(a, "Secret Client");

        getJson("/api/v1/invoices/" + id + "/pdf", b.token()).andExpect(status().isNotFound());
        postJson("/api/v1/invoices/" + id + "/email", b.token(), "{}").andExpect(status().isNotFound());
    }

    /** The test profile allows 5 emails per organization per hour (production: 60). */
    @Test
    void emailsAPersonSendsAreCappedPerOrganization() throws Exception {
        Account a = register("Spammy Co");
        String id = issuedInvoice(a, "Target");
        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/invoices/" + id + "/email", a.token(), "{}").andExpect(status().isOk());
        }
        postJson("/api/v1/invoices/" + id + "/email", a.token(), "{}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // Another organization has its own allowance.
        Account b = register("Quiet Co");
        postJson("/api/v1/invoices/" + issuedInvoice(b, "Client") + "/email", b.token(), "{}").andExpect(status().isOk());
    }

    @Test
    void emailGoesToTheClientWithThePdfAttached() throws Exception {
        Account a = register("Mail Co");
        String id = issuedInvoice(a, "Mail Client");

        postJson("/api/v1/invoices/" + id + "/email", a.token(), """
                {"message":"Thanks for the great project!"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("billing@example.com"));

        String messages = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(mailpitApi + "/api/v1/search?query=" + "subject:%22INV-" + YEAR + "-0001%20from%20Mail%20Co%22")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        List<String> subjects = JsonPath.read(messages, "$.messages[*].Subject");
        assertThat(subjects).contains("Invoice INV-" + YEAR + "-0001 from Mail Co");
        List<Integer> attachments = JsonPath.read(messages, "$.messages[*].Attachments");
        assertThat(attachments).contains(1);
    }

    @Test
    void draftsCannotBeEmailedAndAClientWithoutEmailNeedsARecipient() throws Exception {
        Account a = register("Mail Rules Co");
        String client = createClient(a, "C");
        String draft = read(postJson("/api/v1/invoices", a.token(), document(client, "USD", line("x", "1", "5", null))), "$.id");
        postJson("/api/v1/invoices/" + draft + "/email", a.token(), "{}").andExpect(status().isConflict());

        String noEmail = read(postJson("/api/v1/clients", a.token(), """
                {"name":"No Email Client"}"""), "$.id");
        String id = read(postJson("/api/v1/invoices", a.token(), document(noEmail, "USD", line("x", "1", "5", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null);
        postJson("/api/v1/invoices/" + id + "/email", a.token(), "{}").andExpect(status().isBadRequest());
        postJson("/api/v1/invoices/" + id + "/email", a.token(), """
                {"to":"someone@example.com"}""").andExpect(jsonPath("$.sentTo").value("someone@example.com"));
    }

    @Test
    void quotePdfAndEmail() throws Exception {
        Account a = register("Quote Mail Co");
        String client = createClient(a, "Quote Client");
        String id = read(postJson("/api/v1/quotes", a.token(), document(client, "USD", line("Design", "1", "900", null))), "$.id");
        postJson("/api/v1/quotes/" + id + "/email", a.token(), "{}").andExpect(status().isConflict());

        postJson("/api/v1/quotes/" + id + "/send", a.token(), null);
        getJson("/api/v1/quotes/" + id + "/pdf", a.token())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        postJson("/api/v1/quotes/" + id + "/email", a.token(), "{}").andExpect(status().isOk());
    }
}
