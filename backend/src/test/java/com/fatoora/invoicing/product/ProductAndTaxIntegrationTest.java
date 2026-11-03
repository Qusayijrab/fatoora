package com.fatoora.invoicing.product;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductAndTaxIntegrationTest extends IntegrationTest {

    static String taxRate(String name, String rate, boolean isDefault) {
        return """
                {"name":"%s","rate":%s,"isDefault":%s}""".formatted(name, rate, isDefault);
    }

    static String product(String name, String price, String currency, String taxRateId, String sku) {
        return """
                {"name":"%s","unit":"hour","unitPrice":"%s","currency":"%s","taxRateId":%s,"sku":%s}"""
                .formatted(name, price, currency, taxRateId == null ? "null" : "\"" + taxRateId + "\"",
                        sku == null ? "null" : "\"" + sku + "\"");
    }

    @Test
    void productPriceKeepsThreeDecimalsForJod() throws Exception {
        Account a = register("Product Co");
        String vat = read(postJson("/api/v1/tax-rates", a.token(), taxRate("GST", "16", true)).andExpect(status().isCreated()), "$.id");

        postJson("/api/v1/products", a.token(), product("Consulting", "25.500", "JOD", vat, "CONS-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unitPrice.amount").value("25.500"))
                .andExpect(jsonPath("$.unitPrice.amountMinor").value(25500))
                .andExpect(jsonPath("$.unitPrice.currency").value("JOD"))
                .andExpect(jsonPath("$.taxRate.rate").value("16"));
    }

    @Test
    void tooManyDecimalsForTheCurrencyIs400() throws Exception {
        Account a = register("Decimals Co");
        postJson("/api/v1/products", a.token(), product("Bad", "1.005", "USD", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("USD allows 2 decimals, got 1.005"));
        postJson("/api/v1/products", a.token(), product("Bad", "abc", "USD", null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSkuIs409() throws Exception {
        Account a = register("Sku Co");
        postJson("/api/v1/products", a.token(), product("One", "1", "USD", null, "SKU-1")).andExpect(status().isCreated());
        postJson("/api/v1/products", a.token(), product("Two", "2", "USD", null, "SKU-1")).andExpect(status().isConflict());
    }

    @Test
    void cannotUseAnotherOrganizationsTaxRate() throws Exception {
        Account a = register("Tax A");
        Account b = register("Tax B");
        String bRate = read(postJson("/api/v1/tax-rates", b.token(), taxRate("B VAT", "5", false)), "$.id");

        postJson("/api/v1/products", a.token(), product("Sneaky", "1", "USD", bRate, null))
                .andExpect(status().isNotFound());
        postJson("/api/v1/products", a.token(), product("Sneaky", "1", "USD", UUID.randomUUID().toString(), null))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOneDefaultTaxRate() throws Exception {
        Account a = register("Default Co");
        String first = read(postJson("/api/v1/tax-rates", a.token(), taxRate("Standard", "16", true)), "$.id");
        postJson("/api/v1/tax-rates", a.token(), taxRate("Reduced", "4", true)).andExpect(jsonPath("$.isDefault").value(true));

        getJson("/api/v1/tax-rates", a.token())
                .andExpect(jsonPath("$[?(@.isDefault == true)].name").value(org.hamcrest.Matchers.contains("Reduced")))
                .andExpect(jsonPath("$[?(@.id == '" + first + "')].isDefault").value(org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void membersCannotChangeTaxRatesButCanCreateProducts() throws Exception {
        Account owner = register("Roles Co");
        Account member = register("Member own org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");

        postJson("/api/v1/tax-rates", memberToken, taxRate("Nope", "1", false)).andExpect(status().isForbidden());
        postJson("/api/v1/products", memberToken, product("Allowed", "3", "USD", null, null)).andExpect(status().isCreated());
        getJson("/api/v1/tax-rates", memberToken).andExpect(status().isOk());
    }

    @Test
    void anotherOrganizationCannotSeeMyProducts() throws Exception {
        Account a = register("Prod A");
        Account b = register("Prod B");
        String id = read(postJson("/api/v1/products", a.token(), product("Private", "9", "USD", null, null)), "$.id");

        getJson("/api/v1/products/" + id, b.token()).andExpect(status().isNotFound());
        getJson("/api/v1/products", b.token()).andExpect(jsonPath("$.total").value(0));
    }
}
