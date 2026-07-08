package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.finance.constants.ExpenseTransactionStatus;
import com.af.novadesk.api.finance.constants.PaymentMethod;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import com.af.novadesk.api.common.entity.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ExpenseTransaction} (exp_expense_transactions).
 */
@Repository
public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, UUID>,
        JpaSpecificationExecutor<ExpenseTransaction> {

    static Specification<ExpenseTransaction> filterSpec(
            UUID orgId, UUID legalEntityId, String q,
            ExpenseTransactionStatus status, UUID vendorId,
            PaymentMethod paymentMethod,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String reconciliationStatus) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("legalEntity").get("organizationId"), orgId));
            SpecUtils.addIfPresent(p, legalEntityId,          () -> cb.equal(root.get("legalEntity").get("id"), legalEntityId));
            SpecUtils.addLikeIfPresent(p, q, () -> cb.or(
                    SpecUtils.likeLower(cb, root, "description", q),
                    SpecUtils.likeLower(cb, root, "invoiceReceiptNumber", q)
            ));
            SpecUtils.addIfPresent(p, status,                 () -> cb.equal(root.get("transactionStatus"), status));
            SpecUtils.addIfPresent(p, vendorId,               () -> cb.equal(root.get("vendor").get("id"), vendorId));
            SpecUtils.addIfPresent(p, paymentMethod,          () -> cb.equal(root.get("paymentMethod"), paymentMethod));
            SpecUtils.addIfPresent(p, fromDate,               () -> cb.greaterThanOrEqualTo(root.get("expenseDate"), fromDate));
            SpecUtils.addIfPresent(p, toDate,                 () -> cb.lessThanOrEqualTo(root.get("expenseDate"), toDate));
            SpecUtils.addIfPresent(p, minAmount,              () -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            SpecUtils.addIfPresent(p, maxAmount,              () -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            SpecUtils.addLikeIfPresent(p, reconciliationStatus, () -> cb.equal(root.get("reconciliationStatus"), reconciliationStatus));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    /**
     * Fetches a single transaction with all relations eagerly loaded.
     * Used by the detail view to avoid lazy-loading failures outside the persistence context.
     * Sums expense amounts (local currency) for a given entity,
     * considering only POSTED (non-voided) transactions.
     *
     * <p>{@code attachments} is a {@code @OneToMany} — safe to include here because
     * this query targets a single row (no pagination cartesian product risk).</p>
     */
    @EntityGraph(attributePaths = {
            "legalEntity", "vendor", "createdBy",
            "sourceAccount", "chartOfAccount", "attachments"
    })
    Optional<ExpenseTransaction> findWithRelationsById(UUID id);

    /**
     * Paginated list of all transactions scoped to the caller's organization,
     * joined through {@code legalEntity.organizationId}.
     * @return SUM(amount), or null if no POSTED expenses exist
     */
    @Query("SELECT t FROM ExpenseTransaction t WHERE t.legalEntity.organizationId = :orgId")
    Page<ExpenseTransaction> findAllByOrganizationId(
            @Param("orgId") UUID orgId, Pageable pageable);

    /**
     * Paginated list filtered by accounting status (POSTED / VOID).
     */
    @Query("""
           SELECT t FROM ExpenseTransaction t
           WHERE t.legalEntity.organizationId = :orgId
             AND t.transactionStatus = :status
           """)
    Page<ExpenseTransaction> findAllByOrganizationIdAndTransactionStatus(
            @Param("orgId") UUID orgId,
            @Param("status") ExpenseTransactionStatus status,
            Pageable pageable);

    /**
     * Paginated list scoped to a specific legal entity within the org.
     */
    @Query("""
           SELECT t FROM ExpenseTransaction t
           WHERE t.legalEntity.organizationId = :orgId
             AND t.legalEntity.id = :entityId
           """)
    Page<ExpenseTransaction> findAllByOrganizationIdAndLegalEntityId(
            @Param("orgId") UUID orgId,
            @Param("entityId") UUID entityId,
            Pageable pageable);

    /**
     * Paginated list scoped to a specific legal entity and accounting status.
     */
    @Query("""
           SELECT t FROM ExpenseTransaction t
           WHERE t.legalEntity.organizationId = :orgId
             AND t.legalEntity.id = :entityId
             AND t.transactionStatus = :status
           """)
    Page<ExpenseTransaction> findAllByOrganizationIdAndLegalEntityIdAndTransactionStatus(
            @Param("orgId") UUID orgId,
            @Param("entityId") UUID entityId,
            @Param("status") ExpenseTransactionStatus status,
            Pageable pageable);

    /**
     * Org-scoped get by ID — prevents cross-org access.
     * Used by void, attachment upload, and detail endpoints.
     */
    @Query("""
           SELECT t FROM ExpenseTransaction t
           WHERE t.id = :id
             AND t.legalEntity.organizationId = :orgId
           """)
    Optional<ExpenseTransaction> findByIdAndOrganizationId(
            @Param("id") UUID id,
            @Param("orgId") UUID orgId);
    @Query("SELECT SUM(et.amount) " +
           "  FROM ExpenseTransaction et " +
           " WHERE et.legalEntity = :entity " +
           "   AND et.transactionStatus = 'POSTED'")
    java.math.BigDecimal sumAmountByLegalEntity(@Param("entity") LegalEntity entity);

    /**
     * Find unreconciled expenses for a given legal entity.
     * Used by the bank matching engine (LLR-BNK-02).
     *
     * <p>Returns POSTED expenses that have not yet been matched
     * to any bank transaction (matchedBankTransactionId IS NULL).</p>
     */
    @Query("""
           SELECT et FROM ExpenseTransaction et
           JOIN FETCH et.vendor
           WHERE et.legalEntity.id = :entityId
             AND et.transactionStatus = 'POSTED'
             AND et.matchedBankTransactionId IS NULL
           """)
    java.util.List<ExpenseTransaction> findUnreconciledByLegalEntity(
            @Param("entityId") java.util.UUID entityId);
}
