package com.af.novadesk.api.finance.constants;

/**
 * Matching state of a bank transaction (LLR-BNK-01 / BNK-02).
 *
 * <ul>
 *   <li>{@link #UNMATCHED} — Not yet evaluated for matching.</li>
 *   <li>{@link #SUGGESTED} — A potential match has been suggested (score 60-79).</li>
 *   <li>{@link #MATCHED} — Confirmed match against an expense or ledger entry.</li>
 *   <li>{@link #IGNORED} — User chose to ignore this transaction (not reconcile).</li>
 * </ul>
 */
public enum ReconciliationStatus {
    UNMATCHED,
    SUGGESTED,
    MATCHED,
    IGNORED
}
