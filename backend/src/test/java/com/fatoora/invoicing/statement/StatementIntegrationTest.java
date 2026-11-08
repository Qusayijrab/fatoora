package com.fatoora.invoicing.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class StatementIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    String issued(Account a, String client, String amount) throws Exception {
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("Work", "1", amount, null))), "$.id");
        return read(postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk()), "$.id");
    }

    String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void openingBalanceThenEveryMovementWithARunningBalance() throws Exception {
        Account a = register("Statement Co");
        int year = orgToday().getYear();
        String client = createClient(a, "Ledger Client");

        // Before the period: invoice A for 100, 40 paid.
        String invA = issued(a, client, "100");
        postJson("/api/v1/invoices/" + invA + "/payments", a.token(), """
                {"amount":"40","method":"CASH","paidOn":"%s"}""".formatted(LocalDate.of(year, 1, 20))).andExpect(status().isOk());
        jdbc.update("update invoices set issue_date = ? where id = ?::uuid", LocalDate.of(year, 1, 10), invA);

        // In the period: invoice B for 100, a 10 credit note, 50 paid by transfer.
        String invB = issued(a, client, "100");
        String line = read(getJson("/api/v1/invoices/" + invB, a.token()), "$.lines[0].id");
        String cn = read(postJson("/api/v1/invoices/" + invB + "/credit-notes", a.token(),
                creditNote("Discount", creditLine(line, "1", "10"))).andExpect(status().isOk()), "$.creditNotes[0].id");
        postJson("/api/v1/invoices/" + invB + "/payments", a.token(), """
                {"amount":"50","method":"BANK_TRANSFER","reference":"TRX-7","paidOn":"%s"}"""
                .formatted(LocalDate.of(year, 3, 10))).andExpect(status().isOk());
        jdbc.update("update invoices set issue_date = ? where id = ?::uuid", LocalDate.of(year, 3, 5), invB);
        jdbc.update("update invoices set issue_date = ? where id = ?::uuid", LocalDate.of(year, 3, 6), cn);

        byte[] pdf = getJson("/api/v1/clients/" + client + "/statement?from=%d-02-01&to=%d-03-31".formatted(year, year), a.token())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("Statement of account", "Ledger Client", "Balance brought forward", "60.000",
                "INV-" + year + "-0002", "Credit note for INV-" + year + "-0002", "Payment, bank transfer (TRX-7)",
                "Balance due", "JOD 100.000");
        // Invoice A and its payment are before the period: only in the opening balance.
        assertThat(text).doesNotContain("INV-" + year + "-0001 ");
        assertThat(text).doesNotContain("Payment, cash");
    }

    @Test
    void anotherOrganizationCantReadItAndBadRangesAreRefused() throws Exception {
        Account a = register("Private Statement Co");
        String client = createClient(a, "Private Client");
        Account b = register("Snooping Co");
        getJson("/api/v1/clients/" + client + "/statement", b.token()).andExpect(status().isNotFound());
        getJson("/api/v1/clients/" + client + "/statement?from=2026-05-01&to=2026-04-01", a.token())
                .andExpect(status().isBadRequest());
    }
}
