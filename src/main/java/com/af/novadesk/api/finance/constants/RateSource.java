package com.af.novadesk.api.finance.constants;

/**
 * Identifies how an exchange rate was obtained (LLR-FIN-02.3).
 *
 * <p>The source is logged on every ledger entry and capital-injection record
 * to provide a full audit trail of rate provenance.</p>
 */
public enum RateSource {
    /**
     * Rate fetched from an automated external API feed on the transaction date.
     * Highest confidence — no manual intervention required.
     */
    API,

    /**
     * Rate entered manually by an authorised user.
     * Requires a justification note and an approver name (LLR-FIN-02.3).
     */
    MANUAL,

    /**
     * No rate existed for the exact transaction date; the most recent rate
     * within the configured look-back window was used instead (LLR-FIN-02.3).
     */
    LOOKBACK,

    /**
     * Identity rate (1:1) applied when the source and target currencies are
     * the same — no conversion needed.
     */
    IDENTITY
}

