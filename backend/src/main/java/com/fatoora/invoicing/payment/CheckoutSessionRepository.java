package com.fatoora.invoicing.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Not tenant-filtered: looked up from webhooks. Callers scope by the row's organization id. */
public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, UUID> {

    Optional<CheckoutSession> findByProviderAndProviderSessionId(String provider, String providerSessionId);
}
