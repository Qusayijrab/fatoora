package com.fatoora.invoicing.dashboard;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ForecastIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    /** An issued JOD 100.000 invoice (no tax) for the client; returns its id. */
    String issued(Account a, String client) throws Exception {
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("Work", "1", "100", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        return id;
    }

    void dueOn(String invoiceId, LocalDate due) {
        jdbc.update("update invoices set due_date = ? where id = ?::uuid", due, invoiceId);
    }

    String recurring(Account a, String client, String currency, LocalDate start) throws Exception {
        return read(postJson("/api/v1/recurring", a.token(), """
                {"clientId":"%s","name":"Retainer","currency":"%s","frequency":"MONTHLY","startDate":"%s",
                 "paymentTermsDays":10,"autoIssue":false,"autoEmail":false,"lines":[%s]}"""
                .formatted(client, currency, start, line("Retainer", "1", "50", null))).andExpect(status().isCreated()), "$.id");
    }

    @Test
    void openInvoicesAreExpectedAfterTheClientsUsualDelayAndRecurringRunsAreAdded() throws Exception {
        Account a = register("Forecast Co"); // base JOD
        LocalDate today = orgToday();
        String slowPayer = createClient(a, "Slow Payer");
        String newClient = createClient(a, "New Client");

        // History: three invoices the slow payer paid 10 days late.
        for (int i = 0; i < 3; i++) {
            String paid = issued(a, slowPayer);
            postJson("/api/v1/invoices/" + paid + "/payments", a.token(), """
                    {"amount":"100","method":"BANK_TRANSFER","paidOn":"%s"}""".formatted(today.minusDays(30)))
                    .andExpect(jsonPath("$.status").value("PAID"));
            dueOn(paid, today.minusDays(40));
        }
        // Due in 7 days, but this client pays ~10 days late: expected on day 17, i.e. week 2.
        dueOn(issued(a, slowPayer), today.plusDays(7));
        // A client with no history, already 5 days late: counted now.
        dueOn(issued(a, newClient), today.minusDays(5));

        // Monthly 50.000 starting in 3 days, 10-day terms: runs on days 3, ~34, ~64 land inside 12 weeks.
        recurring(a, newClient, "JOD", today.plusDays(3));
        // EUR without any rate (ECB is off in tests): left out, and said so.
        recurring(a, newClient, "EUR", today.plusDays(3));

        getJson("/api/v1/dashboard/forecast", a.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("JOD"))
                .andExpect(jsonPath("$.weeks.length()").value(12))
                .andExpect(jsonPath("$.weeks[0].start").value(today.toString()))
                .andExpect(jsonPath("$.weeks[0].invoiced.amount").value("100.000"))
                .andExpect(jsonPath("$.alreadyLate.amount").value("100.000"))
                .andExpect(jsonPath("$.weeks[1].invoiced.amount").value("0.000"))
                .andExpect(jsonPath("$.weeks[2].invoiced.amount").value("100.000"))
                .andExpect(jsonPath("$.weeks[1].recurring.amount").value("50.000"))
                .andExpect(jsonPath("$.total.amount").value("350.000"))
                .andExpect(jsonPath("$.clientsWithHistory").value(1))
                .andExpect(jsonPath("$.skippedRecurring").value(3));
    }

    @Test
    void anotherOrganizationSeesOnlyItsOwnForecast() throws Exception {
        Account a = register("Forecast A");
        issued(a, createClient(a, "A client"));
        Account b = register("Forecast B");
        getJson("/api/v1/dashboard/forecast", b.token()).andExpect(jsonPath("$.total.amount").value("0.000"));
    }
}
