package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.entity.ExpenseTransaction;

import java.util.UUID;

/**
 * Outbox publisher contract for the {@link ExpenseTransaction} aggregate (LLR-FIN-03).
 * Each method persists an outbox event row inside the caller's active transaction.
 */
public interface ExpenseTransactionOutboxService {

    void publishExpenseCreated(ExpenseTransaction transaction, UUID journalId,
                               UUID orgId, UUID authUserId);

    void publishExpenseVoided(ExpenseTransaction transaction, String voidReason,
                              UUID orgId, UUID authUserId);
}
