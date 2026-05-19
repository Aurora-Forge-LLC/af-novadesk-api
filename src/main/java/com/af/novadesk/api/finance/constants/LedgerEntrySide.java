package com.af.novadesk.api.finance.constants;

/**
 * Side of a double-entry ledger posting (LLR-FIN-02.2).
 *
 * <p>Standard accounting rules:</p>
 * <ul>
 *   <li>DEBIT  increases ASSET / EXPENSE accounts; decreases LIABILITY / EQUITY / REVENUE.</li>
 *   <li>CREDIT decreases ASSET / EXPENSE accounts; increases LIABILITY / EQUITY / REVENUE.</li>
 * </ul>
 * <p>The system enforces {@code SUM(debits) == SUM(credits)} before any journal is persisted.</p>
 */
public enum LedgerEntrySide {
    DEBIT,
    CREDIT
}

