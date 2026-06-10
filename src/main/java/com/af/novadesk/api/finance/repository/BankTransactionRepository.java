package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import com.af.novadesk.api.finance.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link BankTransaction} entity.
 */
@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    /**
     * Returns all transactions for a given bank statement.
     */
    List<BankTransaction> findAllByStatementIdOrderByTransactionDateAsc(UUID statementId);

    /**
     * Returns paginated transactions for a given bank statement.
     */
    Page<BankTransaction> findAllByStatementId(UUID statementId, Pageable pageable);

    /**
     * Returns transactions filtered by reconciliation status for a statement.
     */
    Page<BankTransaction> findAllByStatementIdAndReconciliationStatus(
            UUID statementId, ReconciliationStatus reconciliationStatus, Pageable pageable);

    /**
     * Returns all unmatched transactions for a given bank account (for matching engine).
     */
    List<BankTransaction> findAllByBankAccountIdAndReconciliationStatus(
            UUID bankAccountId, ReconciliationStatus reconciliationStatus);

    /**
     * Counts transactions by reconciliation status for a statement.
     */
    long countByStatementIdAndReconciliationStatus(UUID statementId, ReconciliationStatus reconciliationStatus);

    /**
     * Deletes all transactions for a given statement (used when superseding).
     */
    void deleteAllByStatementId(UUID statementId);
}
