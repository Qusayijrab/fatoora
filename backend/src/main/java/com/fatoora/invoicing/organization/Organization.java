package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.common.BaseEntity;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.organization.dto.UpdateOrganizationRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A business using Fatoora (the "tenant"). Not tenant-scoped itself: it *is* the tenant.
 * The seller details here are printed on invoices and snapshotted at issue.
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

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_id")
    private String taxId;

    @Column(columnDefinition = "citext")
    private String email;

    private String phone;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    protected Organization() {}

    public Organization(String name, String defaultCurrency, String timeZone) {
        this.name = name;
        this.defaultCurrency = defaultCurrency;
        this.timeZone = timeZone;
    }

    void update(UpdateOrganizationRequest r) {
        this.name = r.name().trim();
        this.defaultCurrency = r.defaultCurrency();
        this.timeZone = r.timeZone();
        this.legalName = Strings.clean(r.legalName());
        this.taxId = Strings.clean(r.taxId());
        this.email = r.email() == null ? null : Strings.clean(r.email().toLowerCase(java.util.Locale.ROOT));
        this.phone = Strings.clean(r.phone());
        this.addressLine1 = Strings.clean(r.addressLine1());
        this.addressLine2 = Strings.clean(r.addressLine2());
        this.city = Strings.clean(r.city());
        this.country = Strings.upper(r.country());
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

    public String getLegalName() {
        return legalName;
    }

    public String getTaxId() {
        return taxId;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
