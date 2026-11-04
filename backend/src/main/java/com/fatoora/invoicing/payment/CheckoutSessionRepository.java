package com.fatoora.invoicing.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Not tenant-filtered: looked up from webhooks. Callers scope by the row's organization id. */
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    Optional<CheckoutSession> findByProviderAndProviderSessionId(String provider, String providerSessionId);

    List<CheckoutSession> findByOrganizationIdAndInvoiceIdAndStatus(UUID organizationId, UUID invoiceId,
            CheckoutSession.Status status);
}
