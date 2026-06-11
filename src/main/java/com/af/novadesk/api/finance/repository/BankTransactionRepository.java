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
}
