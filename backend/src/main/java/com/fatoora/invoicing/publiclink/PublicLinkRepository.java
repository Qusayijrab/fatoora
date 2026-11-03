package com.fatoora.invoicing.publiclink;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicLinkRepository extends JpaRepository<PublicLink, String> {

    Optional<PublicLink> findByOrganizationIdAndKindAndTargetIdAndRevokedAtIsNull(
            UUID organizationId, PublicLink.Kind kind, UUID targetId);
}
