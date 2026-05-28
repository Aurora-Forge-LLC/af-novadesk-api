package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.ExpenseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ExpenseAttachment} (exp_expense_attachments).
 */
@Repository
public interface ExpenseAttachmentRepository extends JpaRepository<ExpenseAttachment, UUID> {

    /**
     * Returns all attachments linked to the given expense transaction.
     * Used by the attachment list endpoint (LLR-FIN-03.4).
     */
    List<ExpenseAttachment> findAllByExpenseTransactionId(UUID expenseTransactionId);

    /**
     * Scoped lookup: returns the attachment only if it belongs to the given transaction.
     * Prevents accessing an attachment via a different transaction's ID.
     */
    Optional<ExpenseAttachment> findByIdAndExpenseTransactionId(UUID id, UUID expenseTransactionId);
}
