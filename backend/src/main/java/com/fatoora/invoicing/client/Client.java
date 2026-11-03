package com.fatoora.invoicing.client;

import com.fatoora.invoicing.client.ClientDtos.ClientRequest;
import com.fatoora.invoicing.common.Strings;
import com.fatoora.invoicing.common.tenancy.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Someone the organization bills. Archived instead of deleted: issued invoices keep a
 * snapshot of these details, and the client may come back.
 */
@Entity
@Table(name = "clients")
public class Client extends TenantScopedEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "citext")
    private String email;

    private String phone;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String region;

    @Column(name = "postal_code")
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    /** Preferred currency for new quotes and invoices; null means the organization default. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean archived;

    protected Client() {}

    public Client(ClientRequest request) {
        apply(request);
    }

    void apply(ClientRequest r) {
        this.name = r.name().trim();
        this.email = r.email() == null ? null : Strings.clean(r.email().toLowerCase(java.util.Locale.ROOT));
        this.phone = Strings.clean(r.phone());
        this.taxId = Strings.clean(r.taxId());
        this.addressLine1 = Strings.clean(r.addressLine1());
        this.addressLine2 = Strings.clean(r.addressLine2());
        this.city = Strings.clean(r.city());
        this.region = Strings.clean(r.region());
        this.postalCode = Strings.clean(r.postalCode());
        this.country = Strings.upper(r.country());
        this.currency = Strings.upper(r.currency());
        this.notes = Strings.clean(r.notes());
    }

    void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getTaxId() {
        return taxId;
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

    public String getRegion() {
        return region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isArchived() {
        return archived;
    }
}
