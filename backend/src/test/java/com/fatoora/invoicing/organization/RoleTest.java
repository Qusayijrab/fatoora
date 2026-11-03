package com.fatoora.invoicing.organization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void ownerIsAtLeastEveryRole() {
        assertThat(Role.OWNER.atLeast(Role.OWNER)).isTrue();
        assertThat(Role.OWNER.atLeast(Role.ADMIN)).isTrue();
        assertThat(Role.OWNER.atLeast(Role.MEMBER)).isTrue();
    }

    @Test
    void adminIsAtLeastAdminButNotOwner() {
        assertThat(Role.ADMIN.atLeast(Role.ADMIN)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.MEMBER)).isTrue();
        assertThat(Role.ADMIN.atLeast(Role.OWNER)).isFalse();
    }

    @Test
    void memberIsOnlyAtLeastMember() {
        assertThat(Role.MEMBER.atLeast(Role.MEMBER)).isTrue();
        assertThat(Role.MEMBER.atLeast(Role.ADMIN)).isFalse();
        assertThat(Role.MEMBER.atLeast(Role.OWNER)).isFalse();
    }
}
