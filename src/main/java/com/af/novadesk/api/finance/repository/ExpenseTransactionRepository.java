package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import com.af.novadesk.api.finance.entity.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link ExpenseTransaction} (exp_expense_transactions).
 */
@Repository
public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, UUID> {

    /**
     * Sums expense amounts (local currency) for a given entity,
     * considering only POSTED (non-voided) transactions.
     *
     * @return SUM(amount), or null if no POSTED expenses exist
     */
    @Query("SELECT SUM(et.amount) " +
           "  FROM ExpenseTransaction et " +
           " WHERE et.legalEntity = :entity " +
           "   AND et.transactionStatus = 'POSTED'")
    java.math.BigDecimal sumAmountByLegalEntity(@Param("entity") LegalEntity entity);
}
