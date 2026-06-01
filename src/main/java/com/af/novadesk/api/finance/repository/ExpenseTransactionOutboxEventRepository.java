package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.ExpenseTransactionOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link ExpenseTransactionOutboxEvent} (exp_expense_outbox_events).
 *
 * <p>Used exclusively by {@link com.af.novadesk.api.finance.service.ExpenseTransactionOutboxService}
 * to persist outbox events inside the same transaction as the aggregate change
 * (Transactional Outbox Pattern).</p>
 */
@Repository
public interface ExpenseTransactionOutboxEventRepository
        extends JpaRepository<ExpenseTransactionOutboxEvent, UUID> {
}
