package com.fatoora.invoicing.invoice;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import java.time.LocalDate;
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
                .andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"))
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
        postJson("/api/v1/invoices/" + a2 + "/issue", a.token(), null).andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"));
        postJson("/api/v1/invoices/" + a1 + "/issue", a.token(), null).andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0002"));
        // Another organization has its own sequence.
        postJson("/api/v1/invoices/" + b1 + "/issue", b.token(), null).andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"));
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
                .andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"))
                .andExpect(jsonPath("$.voidReason").value("Wrong client"));

        String paid = draft(owner, client);
        postJson("/api/v1/invoices/" + paid + "/issue", owner.token(), null);
        postJson("/api/v1/invoices/" + paid + "/payments", owner.token(), """
                {"amount":"1334","method":"CASH"}""");
        postJson("/api/v1/invoices/" + paid + "/void", owner.token(), """
                {"reason":"x"}""").andExpect(status().isConflict());

        // Partly paid: voiding would orphan the 10 JOD already received.
        String partly = draft(owner, client);
        postJson("/api/v1/invoices/" + partly + "/issue", owner.token(), null);
        postJson("/api/v1/invoices/" + partly + "/payments", owner.token(), """
                {"amount":"10","method":"CASH"}""");
        postJson("/api/v1/invoices/" + partly + "/void", owner.token(), """
                {"reason":"x"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("credit note")));
        getJson("/api/v1/invoices/" + partly, owner.token()).andExpect(jsonPath("$.status").value("PARTIALLY_PAID"));
    }

    @Test
    void aPaidInvoiceIsCorrectedWithACreditNoteAndARefund() throws Exception {
        Account owner = register("Refund Co");
        String id = draft(owner, createClient(owner, "C"));
        postJson("/api/v1/invoices/" + id + "/issue", owner.token(), null);
        postJson("/api/v1/invoices/" + id + "/payments", owner.token(), """
                {"amount":"1334","method":"BANK_TRANSFER"}""").andExpect(jsonPath("$.status").value("PAID"));

        // Nothing is owed back yet.
        postJson("/api/v1/invoices/" + id + "/refunds", owner.token(), """
                {"amount":"1","method":"BANK_TRANSFER"}""").andExpect(status().isBadRequest());

        String brand = read(getJson("/api/v1/invoices/" + id, owner.token()), "$.lines[0].id");
        // 287.931 + 16% tax = 334.000 credited on the 850.000 design line.
        postJson("/api/v1/invoices/" + id + "/credit-notes", owner.token(),
                creditNote("Price reduced after delivery", creditLine(brand, "1", "287.931")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.balanceDue.amount").value("0.000"))
                .andExpect(jsonPath("$.refundDue.amount").value("334.000"));

        // That line's single unit has been credited: it can't be credited again.
        postJson("/api/v1/invoices/" + id + "/credit-notes", owner.token(),
                creditNote("again", creditLine(brand, "1", null)))
                .andExpect(status().isBadRequest());

        Account member = register("Refund member org");
        addMember(owner, member, "MEMBER");
        String memberToken = read(postJson("/api/v1/auth/switch-org/" + owner.orgId(), member.token(), null), "$.accessToken");
        postJson("/api/v1/invoices/" + id + "/refunds", memberToken, """
                {"amount":"334","method":"BANK_TRANSFER"}""").andExpect(status().isForbidden());

        postJson("/api/v1/invoices/" + id + "/refunds", owner.token(), """
                {"amount":"334.001","method":"BANK_TRANSFER"}""").andExpect(status().isBadRequest());
        postJson("/api/v1/invoices/" + id + "/refunds", owner.token(), """
                {"amount":"334","method":"BANK_TRANSFER","reference":"TRX-9"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountRefunded.amount").value("334.000"))
                .andExpect(jsonPath("$.refundDue.amount").value("0.000"))
                .andExpect(jsonPath("$.payments[1].kind").value("REFUND"));

        // Collected this month is net of the refund: 1334 - 334.
        getJson("/api/v1/dashboard/overview", owner.token())
                .andExpect(jsonPath("$.collectedThisMonth.amount").value("1000.000"));
    }

    @Test
    void anOldDraftIsIssuedDatedTodayUnlessAskedToKeepItsDates() throws Exception {
        Account a = register("Dates Co");
        String client = createClient(a, "C");
        LocalDate today = orgToday();
        LocalDate written = today.minusDays(40);
        String body = """
                {"clientId":"%s","currency":"JOD","issueDate":"%s","dueDate":"%s","lines":[%s]}"""
                .formatted(client, written, written.plusDays(30), line("Work", "1", "100", null));

        String old = read(postJson("/api/v1/invoices", a.token(), body), "$.id");
        postJson("/api/v1/invoices/" + old + "/issue", a.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueDate").value(today.toString()))
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(30).toString()))
                .andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"));

        String kept = read(postJson("/api/v1/invoices", a.token(), body), "$.id");
        postJson("/api/v1/invoices/" + kept + "/issue", a.token(), """
                {"keepDates":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueDate").value(written.toString()))
                .andExpect(jsonPath("$.dueDate").value(written.plusDays(30).toString()));

        String future = read(postJson("/api/v1/invoices", a.token(), """
                {"clientId":"%s","currency":"JOD","issueDate":"%s","lines":[%s]}"""
                .formatted(client, today.plusDays(3), line("Work", "1", "100", null))), "$.id");
        postJson("/api/v1/invoices/" + future + "/issue", a.token(), """
                {"keepDates":true}""").andExpect(status().isBadRequest());
    }

    @Test
    void creditNoteGetsItsOwnNumberAndReducesTheBalance() throws Exception {
        Account a = register("Credit Co");
        String id = draft(a, createClient(a, "C"));
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null);

        String brand = read(getJson("/api/v1/invoices/" + id, a.token()), "$.lines[0].id");
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(),
                creditNote("Discount agreed after delivery", creditLine(brand, "1", "287.931")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountCredited.amount").value("334.000"))
                .andExpect(jsonPath("$.balanceDue.amount").value("1000.000"))
                .andExpect(jsonPath("$.creditNotes[0].number").value("CN-" + YEAR + "-0001"));

        // A credit beyond what was invoiced is refused.
        String copy = read(getJson("/api/v1/invoices/" + id, a.token()), "$.lines[1].id");
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), creditNote("too much", creditLine(copy, "13", null)))
                .andExpect(status().isBadRequest());

        getJson("/api/v1/invoices?kind=CREDIT_NOTE", a.token()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void creditNotesOnlyReturnWhatWasInvoicedLineByLine() throws Exception {
        Account a = register("Returns Co");
        String id = draft(a, createClient(a, "C"));
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null);
        String copy = read(getJson("/api/v1/invoices/" + id, a.token()), "$.lines[1].id"); // 12 x 25.000, 16% tax

        // Return 5 of 12: the credit line copies description, unit price and tax rate from the original.
        String cn = read(postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(),
                        creditNote("Five pages dropped", creditLine(copy, "5", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCredited.amount").value("145.000"))
                .andExpect(jsonPath("$.creditable[1].lineId").value(copy))
                .andExpect(jsonPath("$.creditable[1].quantity").value("7"))
                .andExpect(jsonPath("$.creditable[1].amount.amount").value("203.000")), "$.creditNotes[0].id");
        getJson("/api/v1/invoices/" + cn, a.token())
                .andExpect(jsonPath("$.lines[0].description").value("Bilingual website copy"))
                .andExpect(jsonPath("$.lines[0].unitPrice.amount").value("25.000"))
                .andExpect(jsonPath("$.lines[0].taxRate").value("16"))
                .andExpect(jsonPath("$.total.amount").value("145.000"));

        // Only 7 are left on that line, across all credit notes.
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), creditNote("more", creditLine(copy, "8", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("only 7")));
        // Two lines in one request count together too.
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(),
                        creditNote("split", creditLine(copy, "4", null), creditLine(copy, "4", null)))
                .andExpect(status().isBadRequest());
        // Never above the invoiced price, never a line from another invoice.
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), creditNote("price", creditLine(copy, "1", "25.001")))
                .andExpect(status().isBadRequest());
        String other = draft(a, createClient(a, "D"));
        postJson("/api/v1/invoices/" + other + "/issue", a.token(), null);
        String otherLine = read(getJson("/api/v1/invoices/" + other, a.token()), "$.lines[0].id");
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), creditNote("wrong", creditLine(otherLine, "1", null)))
                .andExpect(status().isBadRequest());

        // The remaining 7 can still be returned.
        postJson("/api/v1/invoices/" + id + "/credit-notes", a.token(), creditNote("rest", creditLine(copy, "7", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCredited.amount").value("348.000"))
                .andExpect(jsonPath("$.creditable[1].quantity").value("0"));
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
        getJson("/api/v1/invoices?q=INV-" + YEAR + "-0001", a.token()).andExpect(jsonPath("$.items", hasSize(1)));
    }
}
