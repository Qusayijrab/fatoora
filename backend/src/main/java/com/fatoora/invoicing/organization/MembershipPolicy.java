package com.fatoora.invoicing.organization;

import com.fatoora.invoicing.common.error.ApiException;

/**
 * Pure rules for who may change memberships. No database, no Spring: easy to unit test,
 * and the controller can't skip them because the service always calls them.
 *
 * <ul>
 *   <li>Only OWNER and ADMIN manage members.</li>
 *   <li>Only an OWNER can create an owner, or change/remove an existing owner.</li>
 *   <li>An organization always keeps at least one OWNER.</li>
 * </ul>
 */
public final class MembershipPolicy {

    private MembershipPolicy() {}

    public static void checkCanAdd(Role actor, Role newRole) {
        requireManager(actor);
        if (newRole == Role.OWNER && actor != Role.OWNER) {
            throw ApiException.forbidden("Only owners can add another owner");
        }
    }

    public static void checkCanChangeRole(Role actor, Role current, Role newRole, long ownerCount) {
        requireManager(actor);
        if ((current == Role.OWNER || newRole == Role.OWNER) && actor != Role.OWNER) {
            throw ApiException.forbidden("Only owners can grant or change the owner role");
        }
        if (current == Role.OWNER && newRole != Role.OWNER && ownerCount <= 1) {
            throw ApiException.conflict("An organization must keep at least one owner");
        }
    }

    public static void checkCanRemove(Role actor, Role target, long ownerCount) {
        requireManager(actor);
        if (target == Role.OWNER && actor != Role.OWNER) {
            throw ApiException.forbidden("Only owners can remove an owner");
        }
        if (target == Role.OWNER && ownerCount <= 1) {
            throw ApiException.conflict("An organization must keep at least one owner");
        }
    }

    private static void requireManager(Role actor) {
        if (!actor.atLeast(Role.ADMIN)) {
            throw ApiException.forbidden("Only owners and admins can manage members");
        }
    }
}
