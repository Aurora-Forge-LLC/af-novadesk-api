package com.af.novadesk.api.finance.constants;

/**
 * How a bank transaction was matched to an expense transaction.
 */
public enum MatchingMethod {

    /** System auto-matched with score &ge; 80. */
    AUTO_MATCHED,

    /** User confirmed a system suggestion (score 60-79). */
    USER_CONFIRMED,

    /** User manually matched the transaction (no system suggestion). */
    MANUAL
}
