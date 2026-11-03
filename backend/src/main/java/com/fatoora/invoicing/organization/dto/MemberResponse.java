package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.organization.Membership;
import com.fatoora.invoicing.organization.Role;
import java.time.Instant;
import java.util.UUID;

/** {@code id} is the membership id, used by PATCH/DELETE /members/{id}. */
public record MemberResponse(UUID id, UUID userId, String email, String fullName, Role role, Instant joinedAt) {

    public static MemberResponse from(Membership m) {
        return new MemberResponse(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getEmail(),
                m.getUser().getFullName(),
                m.getRole(),
                m.getCreatedAt());
    }
}
