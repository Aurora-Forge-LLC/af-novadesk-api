package com.af.novadesk.api.finance.constants;

/**
 * Lifecycle states of an uploaded bank statement.
 *
 * <p>Statements transition: {@link #UPLOADED} → {@link #PARSED}
 * after transactions are extracted, or {@link #FAILED} if parsing fails.
 * {@link #SUPERSEDED} replaces a statement when a newer version is uploaded
 * for the same bank account + period.</p>
 */
public enum StatementStatus {

    /** File stored in MinIO/S3, not yet parsed. */
    UPLOADED,

    /** Transactions successfully extracted and persisted. */
    PARSED,

    /** Replaced by a newer upload for the same bank account + period. */
    SUPERSEDED,

    /** Parsing failed (malformed file, incompatible format). */
    FAILED
}
