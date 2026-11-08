package com.fatoora.invoicing.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    @Test
    void monthlyFromThe31stStaysAtMonthEndWithoutDrifting() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        assertThat(Frequency.MONTHLY.nthDate(jan31, 1, 1)).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(Frequency.MONTHLY.nthDate(jan31, 2, 1)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(Frequency.MONTHLY.nthDate(jan31, 3, 1)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void otherFrequencies() {
        LocalDate start = LocalDate.of(2026, 1, 15);
        assertThat(Frequency.WEEKLY.nthDate(start, 2, 1)).isEqualTo(LocalDate.of(2026, 1, 29));
        assertThat(Frequency.WEEKLY.nthDate(start, 1, 2)).isEqualTo(LocalDate.of(2026, 1, 29));
        assertThat(Frequency.QUARTERLY.nthDate(start, 1, 1)).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(Frequency.YEARLY.nthDate(LocalDate.of(2028, 2, 29), 1, 1)).isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(Frequency.YEARLY.nthDate(LocalDate.of(2028, 2, 29), 4, 1)).isEqualTo(LocalDate.of(2032, 2, 29));
    }

    static RecurringTemplate template(LocalDate start, LocalDate end, LocalDate today) {
        Client client = new Client(new ClientRequest("C", null, null, null, null, null, null, null, null, null, null, null));
        RecurringTemplate t = RecurringTemplate.create(client, "Rent", "JOD", Frequency.MONTHLY, 1, start);
        t.update(client, "Rent", "JOD", Frequency.MONTHLY, 1, start, end, 30, true, false, null, null, List.of(), today);
        return t;
    }

    @Test
    void aStartDateInThePastNeverBackBills() {
        RecurringTemplate t = template(LocalDate.of(2026, 1, 10), null, LocalDate.of(2026, 11, 3));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void advancingPastTheEndDateEndsTheSchedule() {
        RecurringTemplate t = template(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 3));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        t.advance(Instant.now());
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        t.advance(Instant.now());
        assertThat(t.getStatus()).isEqualTo(RecurringTemplate.Status.ENDED);
        assertThat(t.getNextRunDate()).isNull();
    }

    @Test
    void resumingSkipsThePeriodsThatPassedWhilePaused() {
        RecurringTemplate t = template(LocalDate.of(2026, 1, 5), null, LocalDate.of(2026, 1, 5));
        t.pause(Instant.now());
        t.resume(LocalDate.of(2026, 4, 20));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 5, 5));
        assertThat(t.isDue(LocalDate.of(2026, 4, 20))).isFalse();
    }

    @Test
    void threeFailuresInARowPauseItAndResumingRetriesTheMissedPeriod() {
        RecurringTemplate t = template(LocalDate.of(2026, 1, 5), null, LocalDate.of(2026, 1, 5));
        assertThat(t.recordFailure("No exchange rate", Instant.now())).isFalse();
        assertThat(t.recordFailure("No exchange rate", Instant.now())).isFalse();
        assertThat(t.recordFailure("No exchange rate", Instant.now())).isTrue(); // pauses exactly once
        assertThat(t.getStatus()).isEqualTo(RecurringTemplate.Status.PAUSED);
        assertThat(t.isPausedByFailure()).isTrue();
        assertThat(t.recordFailure("again", Instant.now())).isFalse();

        // Unlike a manual pause, the January period was never invoiced, so it is kept.
        t.resume(LocalDate.of(2026, 4, 20));
        assertThat(t.getNextRunDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(t.getFailureCount()).isZero();
        assertThat(t.isPausedByFailure()).isFalse();
    }

    @Test
    void aSuccessfulRunClearsEarlierFailures() {
        RecurringTemplate t = template(LocalDate.of(2026, 1, 5), null, LocalDate.of(2026, 1, 5));
        t.recordFailure("SMTP down", Instant.now());
        t.recordFailure("SMTP down", Instant.now());
        t.advance(Instant.now());
        assertThat(t.getFailureCount()).isZero();
        assertThat(t.getLastError()).isNull();
        t.recordFailure("again", Instant.now());
        assertThat(t.getStatus()).isEqualTo(RecurringTemplate.Status.ACTIVE); // the count restarted
    }
}
