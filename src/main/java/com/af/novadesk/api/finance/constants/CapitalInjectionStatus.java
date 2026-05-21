package com.af.novadesk.api.finance.constants;

/**
 * Lifecycle status of a {@link com.af.novadesk.api.finance.entity.CapitalInjection}
 * (LLR-FIN-02).
 *
 * <ul>
 *   <li>{@code POSTED}          – Successfully persisted with balanced ledger entries.</li>
 *   <li>{@code PENDING_REVIEW}  – Flagged for manual review (e.g., FX rate anomaly).</li>
 *   <li>{@code FAILED}          – Posting failed after header was created (rare).</li>
 *   <li>{@code VOID}            – Voided after posting; compensating reversal required.</li>
 * </ul>
 */
public enum CapitalInjectionStatus {
    POSTED,
    PENDING_REVIEW,
    FAILED,
    VOID
}
