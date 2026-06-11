package com.af.novadesk.api.finance.constants;

/**
 * Lifecycle status of an uploaded bank statement (LLR-BNK-01).
 *
 * <ul>
 *   <li>{@link #UPLOADED} — File stored, not yet parsed.</li>
 *   <li>{@link #PARSED} — File successfully parsed; transactions extracted.</li>
 *   <li>{@link #SUPERSEDED} — Replaced by a newer upload for the same period.</li>
 *   <li>{@link #FAILED} — Parsing failed; user needs to re-upload.</li>
 * </ul>
 */
public enum StatementStatus {
    UPLOADED,
    PARSED,
    SUPERSEDED,
    FAILED
}
