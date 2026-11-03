package com.fatoora.invoicing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway ran, and Hibernate's "validate" passed (otherwise the context wouldn't start).
 * Also checks that the database itself rejects bad data, independent of Java code.
 */
class MigrationIntegrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1MigrationIsApplied() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).contains("1");
    }

    @Test
    void emailUniquenessIsCaseInsensitive() {
        insertUser("Case@Example.com");
        assertThatThrownBy(() -> insertUser("case@example.COM")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidCurrencyIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into organizations (id, name, default_currency) values (?, 'x', 'usd')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownRoleIsRejectedByTheDatabase() {
        UUID user = insertUser(uniqueEmail());
        UUID org = UUID.randomUUID();
        jdbc.update("insert into organizations (id, name, default_currency) values (?, 'x', 'USD')", org);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into memberships (id, organization_id, user_id, role) values (?, ?, ?, 'SUPERUSER')",
                        UUID.randomUUID(), org, user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, full_name) values (?, ?, 'x', 'x')", id, email);
        return id;
    }
}
