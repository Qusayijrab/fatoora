package com.fatoora.invoicing.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fatoora.invoicing.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/** Jobs are driven directly with a chosen "now", so time is fully under the test's control. */
class JobsIntegrationTest extends IntegrationTest {

    static final ZoneId AMMAN = ZoneId.of("Asia/Amman");

    @Autowired
    JobsService jobs;

    @Autowired
    JdbcTemplate jdbc;

    @Value("${test.mailpit.api}")
    String mailpitApi;

    static LocalDate today() {
        return LocalDate.now(AMMAN);
    }

    /** Noon in Amman on the given date. */
    static Instant noon(LocalDate date) {
        return date.atTime(12, 0).atZone(AMMAN).toInstant();
    }

    String recurring(Account a, String clientId, LocalDate start, boolean autoIssue) throws Exception {
        return read(postJson("/api/v1/recurring", a.token(), """
                {"clientId":"%s","name":"Office rent","currency":"JOD","frequency":"MONTHLY","startDate":"%s",
                 "paymentTermsDays":10,"autoIssue":%s,"autoEmail":false,"lines":[%s]}"""
                .formatted(clientId, start, autoIssue, line("Monthly rent", "1", "450", null)))
                .andExpect(status().isCreated()), "$.id");
    }

    int invoiceCount(Account a) {
        return jdbc.queryForObject("select count(*) from invoices where organization_id = ?", Integer.class, a.orgId());
    }

    @Test
    void recurringGeneratesEachPeriodOnceAndIsSafeToRerun() throws Exception {
        Account a = register("Rent Co");
        String client = createClient(a, "Tenant LLC");
        String id = recurring(a, client, today(), true);

        jobs.runRecurring(noon(today()));
        jobs.runRecurring(noon(today())); // same day again: nothing new
        assertThat(invoiceCount(a)).isEqualTo(1);

        // A month later: exactly one more.
        jobs.runRecurring(noon(today().plusMonths(1)));
        jobs.runRecurring(noon(today().plusMonths(1)));
        assertThat(invoiceCount(a)).isEqualTo(2);

        getJson("/api/v1/recurring/" + id + "/runs", a.token())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].invoiceNumber").value("INV-" + today().getYear() + "-0001"))
                .andExpect(jsonPath("$[1].invoiceStatus").value("ISSUED"));
        getJson("/api/v1/recurring/" + id, a.token())
                .andExpect(jsonPath("$.runCount").value(2))
                .andExpect(jsonPath("$.nextRunDate").value(today().plusMonths(2).toString()));
        getJson("/api/v1/invoices?status=ISSUED", a.token())
                .andExpect(jsonPath("$.items[0].total.amount").value("450.000"))
                .andExpect(jsonPath("$.items[0].dueDate").exists());
    }

    @Test
    void anAlreadyClaimedPeriodIsNeverGeneratedTwice() throws Exception {
        Account a = register("Crash Co");
        String id = recurring(a, createClient(a, "C"), today(), true);
        // Simulate a crash after the claim was committed: the period row exists already.
        jdbc.update("insert into recurring_runs (id, organization_id, template_id, period_date) values (?, ?, ?, ?)",
                UUID.randomUUID(), a.orgId(), UUID.fromString(id), today());

        jobs.runRecurring(noon(today()));
        assertThat(invoiceCount(a)).isZero();
        getJson("/api/v1/recurring/" + id, a.token()).andExpect(jsonPath("$.nextRunDate").value(today().plusMonths(1).toString()));
    }

    @Test
    void twoJobInstancesRacingCreateOneInvoice() throws Exception {
        Account a = register("Race Co");
        recurring(a, createClient(a, "C"), today(), false);

        var first = CompletableFuture.runAsync(() -> jobs.runRecurring(noon(today())));
        var second = CompletableFuture.runAsync(() -> jobs.runRecurring(noon(today())));
        CompletableFuture.allOf(first, second).get();
        assertThat(invoiceCount(a)).isEqualTo(1);
    }

    @Test
    void generateNowAndPauseFromTheApi() throws Exception {
        Account a = register("Manual Co");
        String id = recurring(a, createClient(a, "C"), today().plusDays(10), false);

        String invoiceId = read(postJson("/api/v1/recurring/" + id + "/generate-now", a.token(), null).andExpect(status().isOk()), "$.invoiceId");
        getJson("/api/v1/invoices/" + invoiceId, a.token()).andExpect(jsonPath("$.status").value("DRAFT"));

        postJson("/api/v1/recurring/" + id + "/pause", a.token(), null).andExpect(jsonPath("$.status").value("PAUSED"));
        jobs.runRecurring(noon(today().plusYears(1)));
        assertThat(invoiceCount(a)).isEqualTo(1); // only the one generated by hand
        postJson("/api/v1/recurring/" + id + "/generate-now", a.token(), null).andExpect(status().isConflict());
    }

    /** Issues an invoice dated in the past, due on {@code due}. */
    String issuedDue(Account a, String clientId, LocalDate due) throws Exception {
        String id = read(postJson("/api/v1/invoices", a.token(), """
                {"clientId":"%s","currency":"JOD","issueDate":"%s","dueDate":"%s","lines":[%s]}"""
                .formatted(clientId, due.minusDays(14), due, line("Work", "1", "100", null))), "$.id");
        postJson("/api/v1/invoices/" + id + "/issue", a.token(), null).andExpect(status().isOk());
        // Issuing dates it today; put it back in the past to simulate an invoice that has aged.
        jdbc.update("update invoices set issue_date = ?, due_date = ? where id = ?::uuid", due.minusDays(14), due, id);
        return id;
    }

    @Test
    void overdueUsesTheOrganizationsOwnTimeZone() throws Exception {
        Account amman = register("Amman Co"); // registered with Asia/Amman
        Account la = register("LA Co");
        patchJson("/api/v1/organizations/current", la.token(), """
                {"name":"LA Co","defaultCurrency":"USD","timeZone":"America/Los_Angeles"}""").andExpect(status().isOk());

        LocalDate due = today().minusDays(3);
        String ammanInvoice = issuedDue(amman, createClient(amman, "C1"), due);
        String laInvoice = issuedDue(la, createClient(la, "C2"), due);

        // 00:30 in Amman the day after the due date = still the due date in Los Angeles.
        Instant justAfterAmmanMidnight = ZonedDateTime.of(due.plusDays(1).atTime(0, 30), AMMAN).toInstant();
        jobs.markOverdue(justAfterAmmanMidnight);

        getJson("/api/v1/invoices/" + ammanInvoice, amman.token()).andExpect(jsonPath("$.status").value("OVERDUE"));
        getJson("/api/v1/invoices/" + laInvoice, la.token()).andExpect(jsonPath("$.status").value("ISSUED"));

        jobs.markOverdue(noon(due.plusDays(1)));
        getJson("/api/v1/invoices/" + laInvoice, la.token()).andExpect(jsonPath("$.status").value("OVERDUE"));
    }

    @Test
    void remindersGoOutOncePerThresholdWithThePdf() throws Exception {
        Account a = register("Reminder Co");
        String email = "late-" + UUID.randomUUID() + "@example.com";
        String client = read(postJson("/api/v1/clients", a.token(), """
                {"name":"Late Payer","email":"%s"}""".formatted(email)), "$.id");
        LocalDate due = today().minusDays(30);
        String invoiceId = issuedDue(a, client, due);

        // The jobs sweep every organization; assertions look at this invoice only.
        Instant tenDaysLate = noon(due.plusDays(10));
        jobs.markOverdue(tenDaysLate);
        jobs.sendReminders(tenDaysLate);
        assertThat(reminderDays(invoiceId)).containsExactly(7); // the "7 days" reminder
        jobs.sendReminders(tenDaysLate);
        assertThat(reminderDays(invoiceId)).containsExactly(7); // never twice

        Instant fifteenDaysLate = noon(due.plusDays(15));
        jobs.sendReminders(fifteenDaysLate);
        jobs.sendReminders(fifteenDaysLate);
        assertThat(reminderDays(invoiceId)).containsExactly(7, 14);

        String search = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                mailpitApi + "/api/v1/search?query=" + URLEncoder.encode("to:" + email, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        assertThat((Integer) JsonPath.read(search, "$.messages_count")).isEqualTo(2);
        assertThat((String) JsonPath.read(search, "$.messages[0].Subject")).contains("is overdue");

        // Paying stops the reminders.
        postJson("/api/v1/invoices/" + invoiceId + "/payments", a.token(), """
                {"amount":"100","method":"CASH"}""");
        jobs.sendReminders(noon(due.plusDays(40)));
        assertThat(reminderDays(invoiceId)).containsExactly(7, 14);
    }

    java.util.List<Integer> reminderDays(String invoiceId) {
        return jdbc.queryForList("select days_overdue from invoice_reminders where invoice_id = ? order by 1",
                Integer.class, UUID.fromString(invoiceId));
    }

    @Test
    void remindersCanBeSwitchedOff() throws Exception {
        Account a = register("Quiet Co");
        String client = read(postJson("/api/v1/clients", a.token(), """
                {"name":"Late","email":"quiet-%s@example.com"}""".formatted(UUID.randomUUID())), "$.id");
        LocalDate due = today().minusDays(30);
        issuedDue(a, client, due);
        patchJson("/api/v1/organizations/current", a.token(), """
                {"name":"Quiet Co","defaultCurrency":"JOD","timeZone":"Asia/Amman","remindersEnabled":false}""")
                .andExpect(jsonPath("$.remindersEnabled").value(false));

        jobs.markOverdue(noon(due.plusDays(10)));
        jobs.sendReminders(noon(due.plusDays(10)));
        Integer sent = jdbc.queryForObject("select count(*) from invoice_reminders where organization_id = ?", Integer.class, a.orgId());
        assertThat(sent).isZero();
    }

    @Test
    void sentQuotesExpireAfterTheirValidity() throws Exception {
        Account a = register("Expiry Co");
        String client = createClient(a, "C");
        String quote = read(postJson("/api/v1/quotes", a.token(), """
                {"clientId":"%s","currency":"JOD","issueDate":"%s","validUntil":"%s","lines":[%s]}"""
                .formatted(client, today().minusDays(20), today().minusDays(5), line("x", "1", "10", null))), "$.id");
        postJson("/api/v1/quotes/" + quote + "/send", a.token(), null);

        jobs.expireQuotes(noon(today()));
        getJson("/api/v1/quotes/" + quote, a.token()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }
}
