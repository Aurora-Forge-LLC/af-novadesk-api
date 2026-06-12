package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BankTransaction} entities (LLR-BNK-01.4).
 */
@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    /**
     * Find all transactions for a given statement, ordered by transaction date.
     */
    List<BankTransaction> findByStatementIdOrderByTransactionDateAsc(UUID statementId);

    /**
     * Find paginated transactions for a statement, optionally filtered by status.
     */
    @Query("""
        SELECT t FROM BankTransaction t
        WHERE t.statement.id = :statementId
          AND (:status IS NULL OR t.reconciliationStatus = :status)
        ORDER BY t.transactionDate ASC
    """)
    Page<BankTransaction> findByStatementIdAndStatus(
            @Param("statementId") UUID statementId,
            @Param("status") com.af.novadesk.api.finance.constants.ReconciliationStatus status,
            Pageable pageable);

    /**
     * Count transactions for a statement with a given reconciliation status.
     */
    long countByStatementIdAndReconciliationStatus(
            UUID statementId,
            com.af.novadesk.api.finance.constants.ReconciliationStatus status);

    /**
     * Dynamic query for unmatched transactions with filters (LLR-BNK-03.1).
     * Supports: date range, bank account, amount range, description search, sorting.
     */
    @Query("""
        SELECT t FROM BankTransaction t
        JOIN FETCH t.statement s
        JOIN FETCH s.bankAccount ba
        WHERE t.reconciliationStatus = 'UNMATCHED'
          AND (:entityId IS NULL OR t.legalEntity.id = :entityId)
          AND (:bankAccountId IS NULL OR t.bankAccount.id = :bankAccountId)
          AND (:dateFrom IS NULL OR t.transactionDate >= :dateFrom)
          AND (:dateTo IS NULL OR t.transactionDate <= :dateTo)
          AND (:amountMin IS NULL OR t.amount <= :amountMin)
          AND (:amountMax IS NULL OR t.amount >= :amountMax)
          AND (:search IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY
          CASE WHEN :sortBy = 'transactionDate' AND :sortDir = 'ASC' THEN t.transactionDate END ASC,
          CASE WHEN :sortBy = 'transactionDate' AND :sortDir = 'DESC' THEN t.transactionDate END DESC,
          CASE WHEN :sortBy = 'amount' AND :sortDir = 'ASC' THEN t.amount END ASC,
          CASE WHEN :sortBy = 'amount' AND :sortDir = 'DESC' THEN t.amount END DESC
    """)
    Page<BankTransaction> findUnmatchedWithFilters(
            @Param("entityId") UUID entityId,
            @Param("bankAccountId") UUID bankAccountId,
            @Param("dateFrom") java.time.LocalDate dateFrom,
            @Param("dateTo") java.time.LocalDate dateTo,
            @Param("amountMin") java.math.BigDecimal amountMin,
            @Param("amountMax") java.math.BigDecimal amountMax,
            @Param("search") String search,
            @Param("sortBy") String sortBy,
            @Param("sortDir") String sortDir,
            Pageable pageable);
}
