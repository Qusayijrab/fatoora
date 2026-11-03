package com.fatoora.invoicing.invoice;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Invoice lifecycle (CLAUDE.md rule 5). The allowed moves are data, in one table:
 *
 * <pre>
 * DRAFT ─────────► ISSUED ──► PARTIALLY_PAID ──► PAID
 *   │                │  └──────────┬──────────────▲
 *   │                ▼             ▼              │
 *   │             OVERDUE ─────────────────────────┘
 *   └──► VOID ◄── (any state that is not PAID)
 * </pre>
 * A partial payment on an OVERDUE invoice keeps it OVERDUE (it is still late).
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    VOID;

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(ISSUED, VOID),
            ISSUED, EnumSet.of(PARTIALLY_PAID, PAID, OVERDUE, VOID),
            PARTIALLY_PAID, EnumSet.of(PAID, OVERDUE, VOID),
            OVERDUE, EnumSet.of(OVERDUE, PAID, VOID),
            PAID, EnumSet.noneOf(InvoiceStatus.class),
            VOID, EnumSet.noneOf(InvoiceStatus.class));

    public boolean canMoveTo(InvoiceStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Issued and not finished: money can still arrive. */
    public boolean isOpen() {
        return this == ISSUED || this == PARTIALLY_PAID || this == OVERDUE;
    }
}
