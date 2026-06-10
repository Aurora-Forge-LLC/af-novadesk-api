package com.af.novadesk.api.finance.constants;

/**
 * Matching state of a bank transaction against internal expense records.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@link #UNMATCHED} → {@link #SUGGESTED} (score 60-79) or {@link #MATCHED} (score &ge; 80)</li>
 *   <li>{@link #SUGGESTED} → {@link #MATCHED} (user accepts) or {@link #UNMATCHED} (user rejects)</li>
 *   <li>{@link #UNMATCHED} → {@link #IGNORED} (user marks as not reconcilable)</li>
 * </ul>
 * </p>
 */
public enum ReconciliationStatus {

    /** No match found yet. Default initial state after parsing. */
    UNMATCHED,

    /** System suggested a medium-confidence match (score 60-79) awaiting user review. */
    SUGGESTED,

    /** Successfully matched (auto-matched or user-confirmed). */
    MATCHED,

    /** User marked this transaction as not reconcilable (e.g. transfer between own accounts). */
    IGNORED
}
