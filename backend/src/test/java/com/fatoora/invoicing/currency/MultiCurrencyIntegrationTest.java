package com.fatoora.invoicing.currency;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MultiCurrencyIntegrationTest extends IntegrationTest {

    static String today() {
        return LocalDate.now(ZoneId.of("Asia/Amman")).toString();
    }

    String invoice(Account a, String client, String currency, String price) throws Exception {
        return read(postJson("/api/v1/invoices", a.token(), """
                {"clientId":"%s","currency":"%s","issueDate":"%s","lines":[%s]}"""
                .formatted(client, currency, today(), line("Work", "1", price, null))), "$.id");
    }

    @Test
    void foreignCurrencyInvoicesFreezeTheRateAtIssueAndNeverRecompute() throws Exception {
        Account a = register("FX Co"); // base currency JOD
        String client = createClient(a, "US Client");

        String first = invoice(a, client, "USD", "100");
        postJson("/api/v1/invoices/" + first + "/issue", a.token(), null)
                .andExpect(jsonPath("$.baseCurrency").value("JOD"))
                .andExpect(jsonPath("$.exchangeRate").value("0.709"))
                .andExpect(jsonPath("$.rateSource").value("PEG"))
                .andExpect(jsonPath("$.totalBase.amount").value("70.900"));

        // The business now uses its bank's rate.
        postJson("/api/v1/exchange-rates", a.token(), """
                {"currency":"USD","rate":0.71}""").andExpect(status().isCreated());
        String second = invoice(a, client, "USD", "100");
        postJson("/api/v1/invoices/" + second + "/issue", a.token(), null)
                .andExpect(jsonPath("$.exchangeRate").value("0.71"))
                .andExpect(jsonPath("$.rateSource").value("MANUAL"))
                .andExpect(jsonPath("$.totalBase.amount").value("71.000"));

        // The first invoice keeps its frozen rate.
        getJson("/api/v1/invoices/" + first, a.token())
                .andExpect(jsonPath("$.exchangeRate").value("0.709"))
                .andExpect(jsonPath("$.totalBase.amount").value("70.900"));
    }

    @Test
    void withoutARateTheInvoiceStaysADraftAndNoNumberIsUsed() throws Exception {
        Account a = register("No Rate Co");
        String client = createClient(a, "Euro Client");
        String eur = invoice(a, client, "EUR", "100");

        postJson("/api/v1/invoices/" + eur + "/issue", a.token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("No exchange rate from EUR to JOD")));
        getJson("/api/v1/invoices/" + eur, a.token())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value(nullValue()));

        // The next issued invoice still gets 0001.
        String jod = invoice(a, client, "JOD", "10");
        postJson("/api/v1/invoices/" + jod + "/issue", a.token(), null)
                .andExpect(jsonPath("$.number").value("INV-" + LocalDate.now(ZoneId.of("Asia/Amman")).getYear() + "-0001"));

        // After adding a manual EUR rate, it can be issued.
        postJson("/api/v1/exchange-rates", a.token(), """
                {"currency":"EUR","rate":0.77}""");
        postJson("/api/v1/invoices/" + eur + "/issue", a.token(), null).andExpect(jsonPath("$.totalBase.amount").value("77.000"));
    }

    @Test
    void ratePreviewAndPermissions() throws Exception {
        Account owner = register("Preview Co");
        getJson("/api/v1/exchange-rates/preview?currency=SAR", owner.token())
                .andExpect(jsonPath("$.rate").value("0.1890666667"))
                .andExpect(jsonPath("$.source").value("PEG"));
        getJson("/api/v1/exchange-rates/preview?currency=GBP", owner.token()).andExpect(jsonPath("$.rate").value(nullValue()));
        postJson("/api/v1/exchange-rates", owner.token(), """
                {"currency":"JOD","rate":1}""").andExpect(status().isBadRequest());

        Account member = register("Member Org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        postJson("/api/v1/exchange-rates", memberToken, """
                {"currency":"GBP","rate":0.9}""").andExpect(status().isForbidden());
    }

    @Test
    void dashboardAddsUpEverythingInTheBaseCurrencyWithFrozenRates() throws Exception {
        Account a = register("Dash Co");
        Account other = register("Other Dash Co");
        String client = createClient(a, "Big Client");

        String jod = invoice(a, client, "JOD", "100");
        String usd = invoice(a, client, "USD", "100");
        postJson("/api/v1/invoices/" + jod + "/issue", a.token(), null);
        postJson("/api/v1/invoices/" + usd + "/issue", a.token(), null);
        invoice(a, client, "JOD", "999"); // a draft: not counted as money owed
        postJson("/api/v1/invoices/" + usd + "/payments", a.token(), """
                {"amount":"50","method":"BANK_TRANSFER"}""");

        getJson("/api/v1/dashboard/overview", a.token())
                .andExpect(jsonPath("$.baseCurrency").value("JOD"))
                // 100.000 + (100 - 50) USD x 0.709 = 135.450
                .andExpect(jsonPath("$.outstanding.amount").value("135.450"))
                .andExpect(jsonPath("$.openInvoices").value(2))
                .andExpect(jsonPath("$.collectedThisMonth.amount").value("35.450"))
                .andExpect(jsonPath("$.invoicedThisMonth.amount").value("170.900"))
                .andExpect(jsonPath("$.drafts").value(1))
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.aging[0].amount.amount").value("135.450"))
                .andExpect(jsonPath("$.topClients[0].name").value("Big Client"))
                .andExpect(jsonPath("$.activity[0].summary").exists());

        getJson("/api/v1/dashboard/overview", other.token())
                .andExpect(jsonPath("$.outstanding.amount").value("0.000"))
                .andExpect(jsonPath("$.topClients", hasSize(0)));
    }

    @Test
    void creditNotesReuseTheInvoicesFrozenRate() throws Exception {
        Account a = register("CN FX Co");
        String usd = invoice(a, createClient(a, "C"), "USD", "100");
        postJson("/api/v1/invoices/" + usd + "/issue", a.token(), null);
        String cnId = read(postJson("/api/v1/invoices/" + usd + "/credit-notes", a.token(), """
                {"reason":"Discount","lines":[%s]}""".formatted(line("Discount", "1", "20", null))), "$.creditNotes[0].id");
        getJson("/api/v1/invoices/" + cnId, a.token())
                .andExpect(jsonPath("$.exchangeRate").value("0.709"))
                .andExpect(jsonPath("$.totalBase.amount").value("14.180"));
    }
}
