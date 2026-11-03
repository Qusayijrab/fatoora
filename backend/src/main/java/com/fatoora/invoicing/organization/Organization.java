package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A business using Fatoora (the "tenant"). Not tenant-scoped itself: it *is* the tenant.
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    /** CHAR(3) in SQL, so Hibernate must bind it as CHAR rather than VARCHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    protected Organization() {}

    public Organization(String name, String defaultCurrency, String timeZone) {
        this.name = name;
        this.defaultCurrency = defaultCurrency;
        this.timeZone = timeZone;
    }

    public String getName() {
        return name;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
