package com.af.novadesk.api.finance.constants;

/**
 * Lifecycle state of a suggested match between a bank transaction
 * and an expense transaction (LLR-BNK-02.4).
 */
public enum SuggestionStatus {
    /** Awaiting user review. */
    PENDING,
    /** User confirmed the match. */
    ACCEPTED,
    /** User rejected the match. */
    REJECTED
}
