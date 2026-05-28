package com.af.novadesk.api.finance.constants;

/**
 * Accounting lifecycle state of an {@link com.af.novadesk.api.finance.entity.ExpenseTransaction}.
 *
 * <p>Distinct from the inherited {@code Status} (ACTIVE / INACTIVE) which handles
 * soft-delete. This enum tracks the accounting state of the transaction itself.</p>
 *
 * <ul>
 *   <li>{@code POSTED}  – double-entry ledger entries have been created; transaction is final.</li>
 *   <li>{@code VOID}    – transaction has been cancelled; offsetting ledger entries were posted.</li>
 * </ul>
 */
public enum ExpenseTransactionStatus {

    /**
     * The expense has been recorded and double-entry ledger entries have been posted.
     * This is the default state after a successful save (LLR-FIN-03.2).
     */
    POSTED,

    /**
     * The expense has been voided. Offsetting ledger entries have been created
     * to reverse the original posting. A voided transaction is immutable.
     */
    VOID
}
