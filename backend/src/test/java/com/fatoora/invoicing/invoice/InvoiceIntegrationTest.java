package com.fatoora.invoicing.invoice;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import org.junit.jupiter.api.Test;

class InvoiceIntegrationTest extends IntegrationTest {

    final java.util.Map<java.util.UUID, String> gstByOrg = new java.util.HashMap<>();

    /** The org's 16% rate, created once per organization. */
    String gst(Account a) throws Exception {
        String existing = gstByOrg.get(a.orgId());
        if (existing != null) {
            return existing;
        }
        String id = read(postJson("/api/v1/tax-rates", a.token(), """
                {"name":"GST","rate":16,"isDefault":true}""").andExpect(status().isCreated()), "$.id");
        gstByOrg.put(a.orgId(), id);
        return id;
    }

    /** A 1,334.000 JOD draft (850 + 12 x 25, 16% tax). */
    String draft(Account a, String clientId) throws Exception {
        String tax = gst(a);
        return read(postJson("/api/v1/invoices", a.token(), document(clientId, "JOD",
                        line("Brand identity design", "1", "850", tax),
                        line("Bilingual website copy", "12", "25.000", tax)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value(nullValue()))
                .andExpect(jsonPath("$.subtotal.amount").value("1150.000"))
                .andExpect(jsonPath("$.tax.amount").value("184.000"))
                .andExpect(jsonPath("$.total.amount").value("1334.000"))
                .andExpect(jsonPath("$.lines", hasSize(2))), "$.id");
    }

    @Test
    void fullLifecycleIssuePartialPaymentFullPayment() throws Exception {
        Account a = register("Lifecycle Co");
        String client = createClient(a, "Petra Design Studio");
        String id = draft(a, client);

        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.number").value("INV-2026-0001"))
                .andExpect(jsonPath("$.billTo.name").value("Petra Design Studio"))
                .andExpect(jsonPath("$.seller.name").value("Lifecycle Co"));

        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"500","method":"BANK_TRANSFER","reference":"TRX-1"}""")
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.balanceDue.amount").value("834.000"));

        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"834.000","method":"CASH"}""")
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.balanceDue.amount").value("0.000"))
                .andExpect(jsonPath("$.payments", hasSize(2)));
    }

    @Test
    void numbersAreSequentialPerOrganizationAndDraftsHaveNone() throws Exception {
        Account a = register("Numbering A");
        Account b = register("Numbering B");
        String ca = createClient(a, "Client A");
        String cb = createClient(b, "Client B");

        String a1 = draft(a, ca);
        String a2 = draft(a, ca);
        String b1 = draft(b, cb);

        // Issue out of creation order: numbers follow issue order, not creation order.
        postJson("/api/v1/invoices/" + a2 + "/issue", a.token(), null).andExpect(jsonPath("$.number").value("INV-2026-0001"));
        postJson("/api/v1/invoices/" + a1 + "/issue", a.token(), null).andExpect(jsonPath("$.number").value("INV-2026-0002"));
        // Another organization has its own sequence.
        postJson("/api/v1/invoices/" + b1 + "/issue", b.token(), null).andExpect(jsonPath("$.number").value("INV-2026-0001"));
    }

    @Test
    void issuedInvoicesAreImmutableAndKeepTheirClientSnapshot() throws Exception {
        Account a = register("Immutable Co");
        String client = createClient(a, "Original Name LLC");
        String id = draft(a, client);
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());

        putJson("/api/v1/invoices/" + id, a.token(), document(client, "JOD", line("Sneaky edit", "1", "1", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Issued invoices cannot be edited. Void it or issue a credit note instead."));
        deleteJson("/api/v1/invoices/" + id, a.token()).andExpect(status().isConflict());

        // Renaming the client later does not touch the issued invoice.
        putJson("/api/v1/clients/" + client, a.token(), """
                {"name":"Renamed Holding"}""").andExpect(status().isOk());
        getJson("/api/v1/invoices/" + id, a.token())
                .andExpect(jsonPath("$.billTo.name").value("Original Name LLC"))
                .andExpect(jsonPath("$.billTo.taxId").value("TX-1"))
                .andExpect(jsonPath("$.total.amount").value("1334.000"));
    }

    @Test
    void draftsCanBeEditedAndDeleted() throws Exception {
        Account a = register("Draft Co");
        String client = createClient(a, "Client");
        String id = draft(a, client);

        putJson("/api/v1/invoices/" + id, a.token(), document(client, "USD", line("Hour", "2.5", "40", null)))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.total.amount").value("100.00"));
        deleteJson("/api/v1/invoices/" + id, a.token()).andExpect(status().isNoContent());
        getJson("/api/v1/invoices/" + id, a.token()).andExpect(status().isNotFound());
    }

    @Test
    void cannotOverpayOrPayADraft() throws Exception {
        Account a = register("Overpay Co");
        String id = draft(a, createClient(a, "C"));
        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"1","method":"CASH"}""").andExpect(status().isConflict());

        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null);
        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"1334.001","method":"CASH"}""").andExpect(status().isBadRequest());
        postJson("/api/v1/invoices/" + id + "/payments", a.token(), """
                {"amount":"1.0001","method":"CASH"}""").andExpect(status().isBadRequest());
    }

    @Test
    void voidNeedsAdminAndPaidInvoicesCannotBeVoided() throws Exception {
        Account owner = register("Void Co");
        String client = createClient(owner, "C");
        String id = draft(owner, client);
        postJson("/api/v1/invoices/" + id + "/issue", owner.token(), null);

        Account member = register("Member org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        postJson("/api/v1/invoices/" + id + "/void", memberToken, """
                {"reason":"mistake"}""").andExpect(status().isForbidden());

        postJson("/api/v1/invoices/" + id + "/void", owner.token(), """
                {"reason":"Wrong client"}""")
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.number").value("INV-2026-0001"))
                .andExpect(jsonPath("$.voidReason").value("Wrong client"));

        String paid = draft(owner, client);
        postJson("/api/v1/invoices/" + paid + "/issue", owner.token(), null);
        postJson("/api/v1/invoices/" + paid + "/payments", owner.token(), """
                {"amount":"1334","method":"CASH"}""");
        postJson("/api/v1/invoices/" + paid + "/void", owner.token(), """
                {"reason":"x"}""").andExpect(status().isConflict());
    }

    @Test
    void creditNoteGetsItsOwnNumberAndReducesTheBalance() throws Exception {
        Account a = register("Credit Co");
        String id = draft(a, createClient(a, "C"));
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null);

        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), """
                {"reason":"Discount agreed after delivery","lines":[%s]}""".formatted(line("Goodwill discount", "1", "334", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountCredited.amount").value("334.000"))
                .andExpect(jsonPath("$.balanceDue.amount").value("1000.000"))
                .andExpect(jsonPath("$.creditNotes[0].number").value("CN-2026-0001"));

        // A credit larger than the balance is refused.
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), """
                {"reason":"too much","lines":[%s]}""".formatted(line("x", "1", "1000.001", null)))
                .andExpect(status().isBadRequest());

        getJson("/api/v1/invoices?kind=CREDIT_NOTE", a.token()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void anotherOrganizationCannotTouchMyInvoices() throws Exception {
        Account a = register("Private Inv A");
        Account b = register("Private Inv B");
        String clientA = createClient(a, "A's client");
        String id = draft(a, clientA);

        getJson("/api/v1/invoices/" + id, b.token()).andExpect(status().isNotFound());
        postJson("/api/v1/invoices/" + id + "/issue", b.token(), null).andExpect(status().isNotFound());
        postJson("/api/v1/invoices/" + id + "/payments", b.token(), """
                {"amount":"1","method":"CASH"}""").andExpect(status().isNotFound());
        deleteJson("/api/v1/invoices/" + id, b.token()).andExpect(status().isNotFound());
        getJson("/api/v1/invoices", b.token()).andExpect(jsonPath("$.total").value(0));

        // B cannot bill A's client either.
        postJson("/api/v1/invoices", b.token(), document(clientA, "JOD", line("x", "1", "1", null)))
                .andExpect(status().isNotFound());
        // ...nor use A's tax rate on a line.
        String aTax = gst(a);
        String clientB = createClient(b, "B's client");
        postJson("/api/v1/invoices", b.token(), document(clientB, "JOD", line("x", "1", "1", aTax)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStatusAndSearch() throws Exception {
        Account a = register("List Co");
        String c1 = createClient(a, "Alpha Trading");
        String c2 = createClient(a, "Beta Foods");
        String i1 = draft(a, c1);
        draft(a, c2);
        postJson("/api/v1/invoices/" + i1 + "/issue", a.token(), null);

        getJson("/api/v1/invoices", a.token()).andExpect(jsonPath("$.total").value(2));
        getJson("/api/v1/invoices?status=ISSUED", a.token()).andExpect(jsonPath("$.items", hasSize(1)));
        getJson("/api/v1/invoices?q=beta", a.token()).andExpect(jsonPath("$.items[0].client.name").value("Beta Foods"));
        getJson("/api/v1/invoices?q=INV-2026-0001", a.token()).andExpect(jsonPath("$.items", hasSize(1)));
    }
}
