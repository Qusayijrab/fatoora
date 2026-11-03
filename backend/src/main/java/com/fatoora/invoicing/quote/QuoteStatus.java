package com.fatoora.invoicing.quote;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Quote lifecycle (CLAUDE.md rule 5):
 * DRAFT -> SENT -> ACCEPTED | REJECTED | EXPIRED. An ACCEPTED quote can be converted
 * into an invoice once (tracked by convertedInvoiceId, not by another status).
 */
public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED,
    EXPIRED;

    private static final Map<QuoteStatus, Set<QuoteStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SENT),
            SENT, EnumSet.of(ACCEPTED, REJECTED, EXPIRED),
            ACCEPTED, EnumSet.noneOf(QuoteStatus.class),
            REJECTED, EnumSet.noneOf(QuoteStatus.class),
            EXPIRED, EnumSet.noneOf(QuoteStatus.class));

    public boolean canMoveTo(QuoteStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
