package com.fatoora.invoicing.organization.dto;

import com.fatoora.invoicing.organization.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {}
