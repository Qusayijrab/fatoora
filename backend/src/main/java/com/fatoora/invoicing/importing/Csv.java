package com.fatoora.invoicing.importing;

import java.util.ArrayList;
import java.util.List;

/**
 * A small RFC 4180 reader: quoted fields, doubled quotes inside them, commas and line
 * breaks inside quotes, CRLF or LF. Excel's "CSV UTF-8" adds a byte-order mark, and in
 * locales that write decimals with a comma (Arabic, most of Europe) it separates fields
 * with ';' instead, so the separator is taken from the header line.
 */
final class Csv {

    private Csv() {}

    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char sep = separator(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == sep) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addIfNotBlank(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("A quoted field is never closed (a \" is missing)");
        }
        row.add(field.toString());
        addIfNotBlank(rows, row);
        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(f -> !f.isBlank())) {
            rows.add(row);
        }
    }

    private static char separator(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        long commas = header.chars().filter(ch -> ch == ',').count();
        long semicolons = header.chars().filter(ch -> ch == ';').count();
        return semicolons > commas ? ';' : ',';
    }
}
