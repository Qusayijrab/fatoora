package com.fatoora.invoicing.organization;

import static com.fatoora.invoicing.organization.Role.ADMIN;
import static com.fatoora.invoicing.organization.Role.MEMBER;
import static com.fatoora.invoicing.organization.Role.OWNER;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fatoora.invoicing.common.error.ApiException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MembershipPolicyTest {

    @Nested
    class Add {
        @Test
        void ownerCanAddAnyRole() {
            for (Role role : Role.values()) {
                assertThatCode(() -> MembershipPolicy.checkCanAdd(OWNER, role)).doesNotThrowAnyException();
            }
        }

        @Test
        void adminCanAddAdminsAndMembers() {
            assertThatCode(() -> MembershipPolicy.checkCanAdd(ADMIN, ADMIN)).doesNotThrowAnyException();
            assertThatCode(() -> MembershipPolicy.checkCanAdd(ADMIN, MEMBER)).doesNotThrowAnyException();
        }

        @Test
        void adminCannotAddOwner() {
            assertStatus(() -> MembershipPolicy.checkCanAdd(ADMIN, OWNER), HttpStatus.FORBIDDEN);
        }

        @Test
        void memberCannotAddAnyone() {
            assertStatus(() -> MembershipPolicy.checkCanAdd(MEMBER, MEMBER), HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class ChangeRole {
        @Test
        void ownerCanDemoteAnotherOwnerWhenTwoOwnersExist() {
            assertThatCode(() -> MembershipPolicy.checkCanChangeRole(OWNER, OWNER, ADMIN, 2))
                    .doesNotThrowAnyException();
        }

        @Test
        void lastOwnerCannotBeDemoted() {
            assertStatus(() -> MembershipPolicy.checkCanChangeRole(OWNER, OWNER, ADMIN, 1), HttpStatus.CONFLICT);
        }

        @Test
        void adminCannotTouchAnOwner() {
            assertStatus(() -> MembershipPolicy.checkCanChangeRole(ADMIN, OWNER, MEMBER, 2), HttpStatus.FORBIDDEN);
        }

        @Test
        void adminCannotPromoteToOwner() {
            assertStatus(() -> MembershipPolicy.checkCanChangeRole(ADMIN, MEMBER, OWNER, 1), HttpStatus.FORBIDDEN);
        }

        @Test
        void adminCanPromoteMemberToAdmin() {
            assertThatCode(() -> MembershipPolicy.checkCanChangeRole(ADMIN, MEMBER, ADMIN, 1))
                    .doesNotThrowAnyException();
        }

        @Test
        void memberCannotChangeRoles() {
            assertStatus(() -> MembershipPolicy.checkCanChangeRole(MEMBER, MEMBER, ADMIN, 1), HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class Remove {
        @Test
        void lastOwnerCannotBeRemoved() {
            assertStatus(() -> MembershipPolicy.checkCanRemove(OWNER, OWNER, 1), HttpStatus.CONFLICT);
        }

        @Test
        void ownerCanRemoveAnotherOwnerWhenTwoExist() {
            assertThatCode(() -> MembershipPolicy.checkCanRemove(OWNER, OWNER, 2)).doesNotThrowAnyException();
        }

        @Test
        void adminCannotRemoveOwner() {
            assertStatus(() -> MembershipPolicy.checkCanRemove(ADMIN, OWNER, 2), HttpStatus.FORBIDDEN);
        }

        @Test
        void adminCanRemoveMember() {
            assertThatCode(() -> MembershipPolicy.checkCanRemove(ADMIN, MEMBER, 1)).doesNotThrowAnyException();
        }

        @Test
        void memberCannotRemoveAnyone() {
            assertStatus(() -> MembershipPolicy.checkCanRemove(MEMBER, MEMBER, 1), HttpStatus.FORBIDDEN);
        }
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(expected);
    }
}
