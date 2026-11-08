package com.fatoora.invoicing.auth.dto;

import com.fatoora.invoicing.organization.dto.OrganizationResponse;
import java.util.UUID;

public record MeResponse(UUID id, String email, String fullName, boolean emailVerified, OrganizationResponse organization) {}
