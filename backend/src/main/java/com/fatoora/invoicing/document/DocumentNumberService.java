package com.fatoora.invoicing.document;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequential document numbers per organization and year: INV-2026-0001, QUO-2026-0001,
 * CN-2026-0001 (CLAUDE.md rule 3).
 *
 * <p>Concurrency: one atomic statement both creates the counter row (first number of the
 * year) and increments it, returning the new value. Postgres row-locks the counter until
 * the caller's transaction ends, so two people issuing at the same moment get 0001 and
 * 0002, never the same number. If the issue fails and rolls back, the increment rolls
 * back too: no gaps.
 */
@Service
public class DocumentNumberService {

    public enum DocType {
        INVOICE("INV"),
        CREDIT_NOTE("CN"),
        QUOTE("QUO");

        final String prefix;

        DocType(String prefix) {
            this.prefix = prefix;
        }
    }

    private static final String NEXT = """
            INSERT INTO document_counters (organization_id, doc_type, year, last_value)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (organization_id, doc_type, year)
            DO UPDATE SET last_value = document_counters.last_value + 1
            RETURNING last_value
            """;

    private final JdbcTemplate jdbc;

    public DocumentNumberService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Must run inside the issuing transaction, so the number and the document commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID organizationId, DocType type, int year) {
        Integer value = jdbc.queryForObject(NEXT, Integer.class, organizationId, type.prefix, year);
        return format(type, year, value);
    }

    static String format(DocType type, int year, int value) {
        return "%s-%d-%04d".formatted(type.prefix, year, value);
    }
}
