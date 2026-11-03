package com.fatoora.invoicing.recurring;

import java.time.LocalDate;

/** How often a recurring invoice repeats. */
public enum Frequency {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY;

    /**
     * The n-th run date counted from the anchor (n = 0 is the start date). Always from the
     * anchor, never from the previous run: a schedule starting on 31 Jan gives 28 Feb,
     * then 31 Mar (not 28 Mar), because java.time clamps each month separately.
     */
    public LocalDate nthDate(LocalDate anchor, int n, int interval) {
        long steps = (long) n * interval;
        return switch (this) {
            case WEEKLY -> anchor.plusWeeks(steps);
            case MONTHLY -> anchor.plusMonths(steps);
            case QUARTERLY -> anchor.plusMonths(steps * 3);
            case YEARLY -> anchor.plusYears(steps);
        };
    }
}
