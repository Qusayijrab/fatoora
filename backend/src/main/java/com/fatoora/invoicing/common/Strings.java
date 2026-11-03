package com.fatoora.invoicing.common;

/** Small helpers for optional text fields coming from forms. */
public final class Strings {

    private Strings() {}

    /** Trims; blank becomes null, so "" and "  " are stored as "not provided". */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(java.util.Locale.ROOT);
    }
}
