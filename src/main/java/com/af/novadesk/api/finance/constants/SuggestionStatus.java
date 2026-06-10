package com.af.novadesk.api.finance.constants;

/**
 * Resolution state of a suggested match between a bank transaction
 * and an expense transaction.
 */
public enum SuggestionStatus {

    /** Awaiting user review. */
    PENDING,

    /** User accepted the suggestion — bank transaction marked as MATCHED. */
    ACCEPTED,

    /** User rejected the suggestion — bank transaction returns to UNMATCHED. */
    REJECTED
}
