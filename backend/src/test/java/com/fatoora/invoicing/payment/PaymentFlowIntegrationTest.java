package com.fatoora.invoicing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import com.fatoora.invoicing.common.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class PaymentFlowIntegrationTest extends IntegrationTest {

    @Autowired
    PaymentGateway gateway;

    @Autowired
    JdbcTemplate jdbc;

    /** An issued JOD 121.500 invoice (3 x 40.500). Returns its id. */
    String issued(Account a) throws Exception {
        String client = createClient(a, "Online Payer");
        String id = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("Consulting", "3", "40.500", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        return id;
    }

    String payToken(Account a, String invoiceId) throws Exception {
        String url = read(postJson("/api/v1/invoices/" + invoiceId + "/payment-link", a.token(), null).andExpect(status().isOk()), "$.url");
        assertThat(url).contains("/pay/");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    String startCheckout(String token) throws Exception {
        String url = read(postJson("/api/v1/public/pay/" + token + "/checkout", null, null).andExpect(status().isOk()), "$.url");
        assertThat(url).contains("/pay/mock/");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    ResultActions deliver(String payload, String signature) throws Exception {
        return mvc.perform(post("/api/v1/webhooks/mock").contentType(MediaType.APPLICATION_JSON).content(payload)
                .header(MockPaymentGateway.SIGNATURE_HEADER, signature));
    }

    @Test
    void payerOpensLinkChecksOutAndTheInvoiceBecomesPaid() throws Exception {
        Account a = register("Online Pay Co");
        String invoiceId = issued(a);
        String token = payToken(a, invoiceId);
        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 256 random bits

        // No login: the token is the permission.
        getJson("/api/v1/public/pay/" + token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("INV-" + YEAR + "-0001"))
                .andExpect(jsonPath("$.organizationName").value("Online Pay Co"))
                .andExpect(jsonPath("$.balanceDue.amount").value("121.500"))
                .andExpect(jsonPath("$.payable").value(true))
                .andExpect(jsonPath("$.lines", hasSize(1)));

        String session = startCheckout(token);
        getJson("/api/v1/public/mock-checkout/" + session, null).andExpect(jsonPath("$.amount.amount").value("121.500"));
        postJson("/api/v1/public/mock-checkout/" + session + "/complete", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PROCESSED"));

        getJson("/api/v1/invoices/" + invoiceId, a.token())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.payments[0].method").value("ONLINE"))
                .andExpect(jsonPath("$.payments[0].provider").value("mock"));
        getJson("/api/v1/public/pay/" + token, null).andExpect(jsonPath("$.payable").value(false));
        postJson("/api/v1/public/pay/" + token + "/checkout", null, null).andExpect(status().isConflict());
    }

    @Test
    void theSameWebhookDeliveredTwiceIsAppliedOnce() throws Exception {
        Account a = register("Replay Co");
        String invoiceId = issued(a);
        String session = startCheckout(payToken(a, invoiceId));
        var event = ((MockPaymentGateway) gateway).completionEvent(a.orgId(), session, Money.parse("50", "JOD"));

        deliver(event.payload(), event.signatureHeader()).andExpect(jsonPath("$.outcome").value("PROCESSED"));
        deliver(event.payload(), event.signatureHeader()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));
        deliver(event.payload(), event.signatureHeader()).andExpect(jsonPath("$.outcome").value("DUPLICATE"));

        getJson("/api/v1/invoices/" + invoiceId, a.token())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.payments", hasSize(1)))
                .andExpect(jsonPath("$.balanceDue.amount").value("71.500"));
        String eventId = com.jayway.jsonpath.JsonPath.read(event.payload(), "$.id");
        Integer rows = jdbc.queryForObject("select count(*) from webhook_events where provider = 'mock' and event_id = ?",
                Integer.class, eventId);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void forgedWebhooksAreRejectedAndNotStored() throws Exception {
        Account a = register("Forgery Co");
        String invoiceId = issued(a);
        String session = startCheckout(payToken(a, invoiceId));
        var event = ((MockPaymentGateway) gateway).completionEvent(a.orgId(), session, Money.parse("121.500", "JOD"));

        // Tampered amount, original signature.
        String tampered = event.payload().replace("121500", "1");
        deliver(tampered, event.signatureHeader()).andExpect(status().isBadRequest());
        deliver(event.payload(), "t=1,v1=deadbeef").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/webhooks/mock").contentType(MediaType.APPLICATION_JSON).content(event.payload()))
                .andExpect(status().isBadRequest());

        getJson("/api/v1/invoices/" + invoiceId, a.token()).andExpect(jsonPath("$.status").value("ISSUED"));
        Integer rows = jdbc.queryForObject("select count(*) from webhook_events where organization_id = ?", Integer.class, a.orgId());
        assertThat(rows).isZero();
    }

    @Test
    void lateWebhookForAPaidInvoiceIsIgnoredNotDoubleCharged() throws Exception {
        Account a = register("Late Hook Co");
        String invoiceId = issued(a);
        String session = startCheckout(payToken(a, invoiceId));
        // Paid by hand while the online checkout was still open.
        postJson("/api/v1/invoices/" + invoiceId + "/payments", a.token(), """
                {"amount":"121.500","method":"BANK_TRANSFER"}""").andExpect(jsonPath("$.status").value("PAID"));

        var event = ((MockPaymentGateway) gateway).completionEvent(a.orgId(), session, Money.parse("121.500", "JOD"));
        deliver(event.payload(), event.signatureHeader()).andExpect(jsonPath("$.outcome").value("IGNORED"));
        getJson("/api/v1/invoices/" + invoiceId, a.token()).andExpect(jsonPath("$.payments", hasSize(1)));
    }

    @Test
    void twoTabsShareOneCheckoutAndAChangedBalanceReplacesIt() throws Exception {
        Account a = register("Two Tabs Co");
        String invoiceId = issued(a);
        String token = payToken(a, invoiceId);

        String first = startCheckout(token);
        assertThat(startCheckout(token)).isEqualTo(first); // same balance: reused, not a second checkout

        // A manual payment changes the balance: the open checkout for the old amount is expired.
        postJson("/api/v1/invoices/" + invoiceId + "/payments", a.token(), """
                {"amount":"21.500","method":"CASH"}""").andExpect(status().isOk());
        getJson("/api/v1/public/mock-checkout/" + first, null).andExpect(jsonPath("$.status").value("EXPIRED"));
        postJson("/api/v1/public/mock-checkout/" + first + "/complete", null, null).andExpect(status().isConflict());

        String second = startCheckout(token);
        assertThat(second).isNotEqualTo(first);
        getJson("/api/v1/public/mock-checkout/" + second, null).andExpect(jsonPath("$.amount.amount").value("100.000"));
        Integer open = jdbc.queryForObject("""
                select count(*) from checkout_sessions where invoice_id = ?::uuid and status = 'OPEN'""", Integer.class, invoiceId);
        assertThat(open).isEqualTo(1);
        assertThat(jdbc.queryForObject("select expires_at is not null from checkout_sessions where provider_session_id = ?",
                Boolean.class, second)).isTrue();
    }

    @Test
    void moneyTakenButNotAppliedIsListedUntilMarkedHandled() throws Exception {
        Account a = register("Problem Co");
        String invoiceId = issued(a);
        String session = startCheckout(payToken(a, invoiceId));
        postJson("/api/v1/invoices/" + invoiceId + "/payments", a.token(), """
                {"amount":"121.500","method":"BANK_TRANSFER"}""").andExpect(jsonPath("$.status").value("PAID"));

        // The client had already paid at the provider before the checkout was expired.
        var event = ((MockPaymentGateway) gateway).completionEvent(a.orgId(), session, Money.parse("121.500", "JOD"));
        deliver(event.payload(), event.signatureHeader()).andExpect(jsonPath("$.outcome").value("IGNORED"));

        String problemId = read(getJson("/api/v1/payment-problems", a.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].invoiceId").value(invoiceId))
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-" + YEAR + "-0001"))
                .andExpect(jsonPath("$[0].detail").value(org.hamcrest.Matchers.containsString("121.500"))), "$[0].id");
        getJson("/api/v1/audit-events?entityId=" + invoiceId, a.token())
                .andExpect(jsonPath("$.items[*].action", org.hamcrest.Matchers.hasItem("online_payment_not_applied")));

        // Another organization sees nothing and cannot resolve it.
        Account b = register("Other Problem Co");
        getJson("/api/v1/payment-problems", b.token()).andExpect(jsonPath("$", hasSize(0)));
        postJson("/api/v1/payment-problems/" + problemId + "/resolve", b.token(), null).andExpect(status().isNotFound());

        postJson("/api/v1/payment-problems/" + problemId + "/resolve", a.token(), null).andExpect(status().isNoContent());
        getJson("/api/v1/payment-problems", a.token()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theBusinessSeesWhenTheClientOpenedTheInvoice() throws Exception {
        Account a = register("Viewed Co");
        String invoiceId = issued(a);
        getJson("/api/v1/invoices/" + invoiceId, a.token())
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.firstViewedAt").doesNotExist());

        String token = payToken(a, invoiceId);
        getJson("/api/v1/public/pay/" + token, null).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/public/pay/" + token + "/pdf")).andExpect(status().isOk());

        getJson("/api/v1/invoices/" + invoiceId, a.token())
                .andExpect(jsonPath("$.viewCount").value(2))
                .andExpect(jsonPath("$.firstViewedAt").isString())
                .andExpect(jsonPath("$.lastViewedAt").isString());
        // Only the first view goes into the history.
        getJson("/api/v1/audit-events?entityId=" + invoiceId, a.token())
                .andExpect(jsonPath("$.items[?(@.action == 'viewed')]", hasSize(1)));
    }

    @Test
    void publicLinksRevealNothingWithoutAValidToken() throws Exception {
        Account a = register("Token Co");
        String invoiceId = issued(a);

        getJson("/api/v1/public/pay/not-a-token", null).andExpect(status().isNotFound());
        getJson("/api/v1/public/pay/" + "A".repeat(43), null).andExpect(status().isNotFound());
        // The invoice id itself is not a token.
        getJson("/api/v1/public/pay/" + invoiceId, null).andExpect(status().isNotFound());
        // Drafts have no payment link.
        String client = createClient(a, "C");
        String draft = read(postJson("/api/v1/invoices", a.token(), document(client, "JOD", line("x", "1", "1", null))), "$.id");
        postJson("/api/v1/invoices/" + draft + "/payment-link", a.token(), null).andExpect(status().isConflict());
        // Another organization cannot mint a link for my invoice.
        Account b = register("Other Token Co");
        postJson("/api/v1/invoices/" + invoiceId + "/payment-link", b.token(), null).andExpect(status().isNotFound());
        // The same invoice always gets the same link.
        assertThat(payToken(a, invoiceId)).isEqualTo(payToken(a, invoiceId));
        assertThat(UUID.randomUUID()).isNotNull();
    }
}
