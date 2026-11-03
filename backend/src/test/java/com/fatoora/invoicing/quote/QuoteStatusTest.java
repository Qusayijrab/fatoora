package com.fatoora.invoicing.quote;

import static com.fatoora.invoicing.quote.QuoteStatus.ACCEPTED;
import static com.fatoora.invoicing.quote.QuoteStatus.DRAFT;
import static com.fatoora.invoicing.quote.QuoteStatus.EXPIRED;
import static com.fatoora.invoicing.quote.QuoteStatus.REJECTED;
import static com.fatoora.invoicing.quote.QuoteStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuoteStatusTest {

    @Test
    void draftOnlyGoesToSent() {
        assertThat(DRAFT.canMoveTo(SENT)).isTrue();
        assertThat(DRAFT.canMoveTo(ACCEPTED)).isFalse();
    }

    @Test
    void sentEndsInOneOfThree() {
        assertThat(SENT.canMoveTo(ACCEPTED)).isTrue();
        assertThat(SENT.canMoveTo(REJECTED)).isTrue();
        assertThat(SENT.canMoveTo(EXPIRED)).isTrue();
        assertThat(SENT.canMoveTo(DRAFT)).isFalse();
    }

    @Test
    void outcomesAreFinal() {
        for (QuoteStatus end : new QuoteStatus[] {ACCEPTED, REJECTED, EXPIRED}) {
            for (QuoteStatus any : QuoteStatus.values()) {
                assertThat(end.canMoveTo(any)).as("%s -> %s", end, any).isFalse();
            }
        }
    }
}
