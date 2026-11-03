package com.fatoora.invoicing.client;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;

class ClientIntegrationTest extends IntegrationTest {

    static String client(String name) {
        return """
                {"name":"%s","email":"Billing@Example.COM","taxId":"JO-998877","city":"Amman","country":"JO","currency":"USD"}"""
                .formatted(name);
    }

    @Test
    void createListSearchUpdateArchive() throws Exception {
        Account a = register("Client Co");

        String id = read(postJson("/api/v1/clients", a.token(), client("Petra Design Studio"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("billing@example.com"))
                .andExpect(jsonPath("$.country").value("JO")), "$.id");
        postJson("/api/v1/clients", a.token(), client("شركة الندى للتجارة")).andExpect(status().isCreated());

        getJson("/api/v1/clients", a.token()).andExpect(jsonPath("$.total").value(2));
        getJson("/api/v1/clients?q=petra", a.token())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Petra Design Studio"));
        getJson("/api/v1/clients?q=الندى", a.token()).andExpect(jsonPath("$.items", hasSize(1)));

        putJson("/api/v1/clients/" + id, a.token(), client("Petra Studio")).andExpect(jsonPath("$.name").value("Petra Studio"));

        postJson("/api/v1/clients/" + id + "/archive", a.token(), null).andExpect(jsonPath("$.archived").value(true));
        getJson("/api/v1/clients", a.token()).andExpect(jsonPath("$.total").value(1));
        getJson("/api/v1/clients?includeArchived=true", a.token()).andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void validationRejectsBadCountryCurrencyAndEmail() throws Exception {
        Account a = register("Strict Clients");
        postJson("/api/v1/clients", a.token(), """
                {"name":"X","email":"nope","country":"Jordan","currency":"usd"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.country").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void anotherOrganizationCannotSeeOrChangeMyClient() throws Exception {
        Account a = register("Org A clients");
        Account b = register("Org B clients");
        String id = read(postJson("/api/v1/clients", a.token(), client("Secret Client")), "$.id");

        getJson("/api/v1/clients/" + id, b.token()).andExpect(status().isNotFound());
        putJson("/api/v1/clients/" + id, b.token(), client("Hijacked")).andExpect(status().isNotFound());
        postJson("/api/v1/clients/" + id + "/archive", b.token(), null).andExpect(status().isNotFound());
        getJson("/api/v1/clients", b.token()).andExpect(jsonPath("$.total").value(0));

        getJson("/api/v1/clients/" + id, a.token()).andExpect(jsonPath("$.name").value("Secret Client"));
    }
}
