package com.fatoora.invoicing.common.tenancy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantProbeRepository extends JpaRepository<TenantProbe, UUID> {}
