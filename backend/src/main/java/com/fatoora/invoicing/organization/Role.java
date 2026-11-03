package com.fatoora.invoicing.organization;

/** Roles inside one organization, from most to least powerful. */
public enum Role {
    OWNER,
    ADMIN,
    MEMBER;

    /** True if this role is the same as, or more powerful than, {@code other}. */
    public boolean atLeast(Role other) {
        return this.ordinal() <= other.ordinal();
    }
}
