package com.fatoora.invoicing.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    String issued(Account a, String client, String... lines) throws Exception {
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", lines)), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        return id;
    }

    void dueOn(String id, LocalDate due) {
        jdbc.update("update invoices set due_date = ? where id = ?::uuid", due, id);
    }

    @Test
    void agingSplitsWhatEachClientOwesByHowLateItIs() throws Exception {
        Account a = register("Aging Co");
        LocalDate today = orgToday();
        String slow = createClient(a, "=SUM(A1:A9)"); // a formula as a name, for the CSV check
        String good = createClient(a, "Good Client");
        dueOn(issued(a, slow, line("Work", "1", "100", null)), today.minusDays(10));
        dueOn(issued(a, slow, line("Work", "1", "200", null)), today.minusDays(100));
        dueOn(issued(a, good, line("Work", "1", "50", null)), today.plusDays(5));

        getJson("/api/v1/reports/aging", a.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].total.amount").value("300.000"))
                .andExpect(jsonPath("$.rows[0].days1to30.amount").value("100.000"))
                .andExpect(jsonPath("$.rows[0].over90.amount").value("200.000"))
                .andExpect(jsonPath("$.rows[1].client").value("Good Client"))
                .andExpect(jsonPath("$.rows[1].notDue.amount").value("50.000"))
                .andExpect(jsonPath("$.totals.total.amount").value("350.000"));

        String csv = new String(getJson("/api/v1/reports/aging.csv", a.token()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Client,Not yet due,");
        // The hostile name is kept as text, never as a formula.
        assertThat(csv).contains("\"'=SUM(A1:A9)\",0.000,100.000,0.000,0.000,200.000,300.000");
        assertThat(csv).contains("Total,50.000,100.000,0.000,0.000,200.000,350.000");
    }

    @Test
    void taxSummaryPerRateIsNetOfCreditNotes() throws Exception {
        Account a = register("Tax Report Co");
        String gst = read(postJson("/api/v1/tax-rates", a.token(), """
                {"name":"GST","rate":"16","isDefault":false}""").andExpect(status().isCreated()), "$.id");
        String client = createClient(a, "Taxed Client");
        String inv = issued(a, client, line("Design", "1", "100", gst), line("Printing", "1", "50", null));
        String designLine = read(getJson("/api/v1/invoices/" + inv, a.token()), "$.lines[0].id");
        postJson("/api/v1/invoices/" + inv + "/credit-notes", a.token(),
                creditNote("Discount", creditLine(designLine, "1", "10"))).andExpect(status().isOk());

        LocalDate today = orgToday();
        getJson("/api/v1/reports/tax?from=%s&to=%s".formatted(today.withDayOfMonth(1), today), a.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].rate").value("16"))
                .andExpect(jsonPath("$.rows[0].name").value("GST"))
                .andExpect(jsonPath("$.rows[0].taxable.amount").value("90.000"))
                .andExpect(jsonPath("$.rows[0].tax.amount").value("14.400"))
                .andExpect(jsonPath("$.rows[1].name").value("No tax"))
                .andExpect(jsonPath("$.rows[1].taxable.amount").value("50.000"))
                .andExpect(jsonPath("$.tax.amount").value("14.400"))
                .andExpect(jsonPath("$.invoices").value(1))
                .andExpect(jsonPath("$.creditNotes").value(1));

        // A period before anything was issued is empty; a backwards period is refused.
        getJson("/api/v1/reports/tax?from=2020-01-01&to=2020-01-31", a.token()).andExpect(jsonPath("$.tax.amount").value("0.000"));
        getJson("/api/v1/reports/tax?from=2026-05-01&to=2026-04-01", a.token()).andExpect(status().isBadRequest());
    }

    @Test
    void reportsOnlyCoverTheCallersOrganization() throws Exception {
        Account a = register("Report A");
        issued(a, createClient(a, "A client"), line("Work", "1", "100", null));
        Account b = register("Report B");
        getJson("/api/v1/reports/aging", b.token()).andExpect(jsonPath("$.totals.total.amount").value("0.000"));
    }
}
