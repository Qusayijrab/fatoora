package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.organization.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Adds an already-registered user to the current org. Note there is no organization id
 * here on purpose: the org always comes from the caller's token.
 */
public record AddMemberRequest(@NotBlank @Email String email, @NotNull Role role) {}
