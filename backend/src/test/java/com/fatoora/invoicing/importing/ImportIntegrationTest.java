package com.fatoora.invoicing.importing;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class ImportIntegrationTest extends IntegrationTest {

    ResultActions upload(String path, Account a, String csv, boolean dryRun) throws Exception {
        return mvc.perform(post(path + "?dryRun=" + dryRun)
                .header("Authorization", "Bearer " + a.token())
                .contentType("text/csv")
                .content(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void clientsArePreviewedThenImportedAllOrNothing() throws Exception {
        Account a = register("Import Co");
        createClient(a, "Existing Client"); // email billing@example.com
        String csv = """
                Name,Email,Tax ID,City,Country,Currency
                "Petra Design, LLC",hello@petra.jo,JO-77,Amman,jo,JOD
                Same As Existing,billing@example.com,,,,
                Bad Email Co,not-an-email,,,,
                Bad Country Co,,,,Jordan,
                """;
        upload("/api/v1/clients/import", a, csv, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(false))
                .andExpect(jsonPath("$.ok").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.errors").value(2))
                .andExpect(jsonPath("$.rows[0].line").value(2))
                .andExpect(jsonPath("$.rows[0].status").value("OK"))
                .andExpect(jsonPath("$.rows[1].status").value("SKIP"))
                .andExpect(jsonPath("$.rows[2].messages[0]").value(containsString("email")))
                .andExpect(jsonPath("$.rows[3].messages[0]").value(containsString("country")));

        // With errors left, nothing is written even when asked to import.
        upload("/api/v1/clients/import", a, csv, false).andExpect(jsonPath("$.imported").value(false));
        getJson("/api/v1/clients", a.token()).andExpect(jsonPath("$.total").value(1));

        String fixed = """
                Name,Email,Tax ID,City,Country,Currency
                "Petra Design, LLC",hello@petra.jo,JO-77,Amman,jo,JOD
                Same As Existing,billing@example.com,,,,
                """;
        upload("/api/v1/clients/import", a, fixed, false)
                .andExpect(jsonPath("$.imported").value(true))
                .andExpect(jsonPath("$.ok").value(1))
                .andExpect(jsonPath("$.skipped").value(1));
        getJson("/api/v1/clients?q=petra", a.token())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Petra Design, LLC"))
                .andExpect(jsonPath("$.items[0].country").value("JO"))
                .andExpect(jsonPath("$.items[0].taxId").value("JO-77"));

        // Importing the same file again creates nothing new.
        upload("/api/v1/clients/import", a, fixed, false)
                .andExpect(jsonPath("$.ok").value(0))
                .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void productsMatchTaxRatesByNameOrPercentAndUseTheCurrencysDecimals() throws Exception {
        Account a = register("Catalog Import Co"); // base JOD
        postJson("/api/v1/tax-rates", a.token(), """
                {"name":"GST","rate":"16","isDefault":true}""").andExpect(status().isCreated());
        String csv = """
                Product;SKU;Unit;Price;Tax
                Logo design;LOGO-1;project;"1,250.500";GST
                Business cards;CARD-1;box;35;16%
                Flyer;FLY-1;item;10.1234;
                Banner;BAN-1;item;20;7%
                """;
        upload("/api/v1/products/import", a, csv, true)
                .andExpect(jsonPath("$.ok").value(2))
                .andExpect(jsonPath("$.errors").value(2))
                .andExpect(jsonPath("$.rows[2].messages[0]").value(containsString("unitPrice")))
                .andExpect(jsonPath("$.rows[3].messages[0]").value(containsString("7%")));

        String good = """
                Product;SKU;Unit;Price;Tax
                Logo design;LOGO-1;project;"1,250.500";GST
                Business cards;CARD-1;box;35;16%
                """;
        upload("/api/v1/products/import", a, good, false).andExpect(jsonPath("$.imported").value(true));
        getJson("/api/v1/products?q=logo", a.token())
                .andExpect(jsonPath("$.items[0].unitPrice.amount").value("1250.500"))
                .andExpect(jsonPath("$.items[0].unitPrice.currency").value("JOD"))
                .andExpect(jsonPath("$.items[0].taxRate.name").value("GST"));
    }

    @Test
    void onlyAdminsImportAndBadFilesGetAClearError() throws Exception {
        Account owner = register("Locked Import Co");
        Account member = register("Member Import Home");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        mvc.perform(post("/api/v1/clients/import").header("Authorization", "Bearer " + memberToken)
                        .contentType("text/csv").content("name\nX\n"))
                .andExpect(status().isForbidden());

        upload("/api/v1/clients/import", owner, "email\nx@example.com\n", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("\"name\" column")));
        upload("/api/v1/clients/import", owner, "name\n", true).andExpect(status().isBadRequest());
    }
}
