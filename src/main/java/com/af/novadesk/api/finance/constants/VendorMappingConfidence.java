package com.af.novadesk.api.finance.constants;

/**
 * Confidence level of a learned vendor pattern mapping from bank
 * statement descriptions to vendor records.
 */
public enum VendorMappingConfidence {

    /** Mapping explicitly confirmed by a finance user. */
    USER_CONFIRMED,

    /** Mapping learned automatically by the system after sufficient usage (&ge; 3 matches). */
    AUTO_LEARNED
}
