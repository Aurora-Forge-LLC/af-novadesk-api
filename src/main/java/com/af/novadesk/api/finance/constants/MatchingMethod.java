package com.af.novadesk.api.finance.constants;

/**
 * How a match between a bank transaction and an expense was established (LLR-BNK-02).
 *
 * <ul>
 *   <li>{@link #AUTO_MATCHED} — Automatically matched by the system (score >= 80).</li>
 *   <li>{@link #USER_CONFIRMED} — System suggested, user confirmed.</li>
 *   <li>{@link #MANUAL} — User manually created the match.</li>
 * </ul>
 */
public enum MatchingMethod {
    AUTO_MATCHED,
    USER_CONFIRMED,
    MANUAL
}
