package com.fatoora.invoicing.invoice;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped automatically through Payment's @TenantId. */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoice_IdOrderByPaidOnAscCreatedAtAsc(UUID invoiceId);

    boolean existsByProviderAndProviderPaymentId(String provider, String providerPaymentId);
}
