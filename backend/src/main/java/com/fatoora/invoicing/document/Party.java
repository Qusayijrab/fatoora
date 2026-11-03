package com.fatoora.invoicing.document;

import com.fatoora.invoicing.client.Client;
import com.fatoora.invoicing.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A frozen copy of a buyer's or seller's details, taken when a document is issued.
 * Later edits to the client or organization never change an issued invoice.
 * Columns are prefixed per use (bill_to_*, seller_*) via @AttributeOverrides.
 */
@Embeddable
public class Party {

    private String name;

    @Column(columnDefinition = "citext")
    private String email;

    private String phone;
    private String taxId;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String region;
    private String postalCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    protected Party() {}

    public static Party of(Client c) {
        Party p = new Party();
        p.name = c.getName();
        p.email = c.getEmail();
        p.phone = c.getPhone();
        p.taxId = c.getTaxId();
        p.addressLine1 = c.getAddressLine1();
        p.addressLine2 = c.getAddressLine2();
        p.city = c.getCity();
        p.region = c.getRegion();
        p.postalCode = c.getPostalCode();
        p.country = c.getCountry();
        return p;
    }

    public static Party of(Organization o) {
        Party p = new Party();
        p.name = o.getLegalName() != null ? o.getLegalName() : o.getName();
        p.email = o.getEmail();
        p.phone = o.getPhone();
        p.taxId = o.getTaxId();
        p.addressLine1 = o.getAddressLine1();
        p.addressLine2 = o.getAddressLine2();
        p.city = o.getCity();
        p.country = o.getCountry();
        return p;
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
}
