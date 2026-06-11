package com.af.novadesk.api.finance.constants;

/**
 * Confidence level of a learned vendor name pattern (LLR-BNK-02.5).
 */
public enum VendorMappingConfidence {
    /** Pattern was explicitly set by a finance user. */
    USER_CONFIRMED,
    /** Pattern was auto-learned after 3+ successful matches. */
    AUTO_LEARNED
}
