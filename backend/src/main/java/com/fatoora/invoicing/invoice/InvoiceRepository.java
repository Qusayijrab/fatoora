package com.fatoora.invoicing.invoice;

import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped automatically through Invoice's @TenantId. */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {

    @EntityGraph(attributePaths = {"lines", "client"})
    Optional<Invoice> findWithLinesById(UUID id);

    /**
     * SELECT ... FOR UPDATE: every change to an invoice (pay, issue, void, credit, edit)
     * locks its row first, so two concurrent payments can't both read the same balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findForUpdate(@Param("id") UUID id);

    /** Plain values, not an entity: reading them never puts a stale Invoice in the persistence context. */
    interface CurrencyAndDate {
        String getCurrency();

        LocalDate getIssueDate();
    }

    @Query("select i.currency as currency, i.issueDate as issueDate from Invoice i where i.id = :id")
    Optional<CurrencyAndDate> findCurrencyAndDate(@Param("id") UUID id);

    List<Invoice> findByCreditedInvoice_IdOrderByIssueDateAscCreatedAtAsc(UUID invoiceId);

    /** How much of one invoice line its credit notes have credited so far. */
    interface CreditedSoFar {
        UUID getLineId();

        BigDecimal getQuantity();

        Long getTotalMinor();
    }

    @Query("""
            select l.creditedLineId as lineId, sum(l.quantity) as quantity, sum(l.totalMinor) as totalMinor
            from InvoiceLine l
            where l.invoice.creditedInvoice.id = :invoiceId and l.creditedLineId is not null
            group by l.creditedLineId""")
    List<CreditedSoFar> creditedLines(@Param("invoiceId") UUID invoiceId);

    long countByKindAndNumberIsNotNull(InvoiceKind kind);

    static Specification<Invoice> matching(InvoiceKind kind, InvoiceStatus status, UUID clientId, String query) {
        return (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("kind"), kind));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (clientId != null) {
                p.add(cb.equal(root.get("client").get("id"), clientId));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                var client = root.join("client");
                p.add(cb.or(
                        cb.like(cb.lower(root.get("number")), like, '\\'),
                        cb.like(cb.lower(root.get("billTo").get("name")), like, '\\'),
                        cb.like(cb.lower(client.get("name")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
