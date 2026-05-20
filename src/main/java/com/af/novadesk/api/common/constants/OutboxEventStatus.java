package com.af.novadesk.api.common.constants;



/**
 *
 *
 * <p>The polling publisher queries for {@code PENDING} rows, attempts delivery,
 * and transitions to {@code PUBLISHED} on success or {@code FAILED} after
 * exhausting retries. {@code DEAD} marks rows moved to the dead-letter log
 * for manual inspection.</p>
 */
public enum OutboxEventStatus {

    /** Inserted by the business transaction; not yet picked up by the poller. */
    PENDING,

    /** Successfully delivered to the message broker / downstream consumer. */
    PUBLISHED,

    /**
     * Delivery failed on the last attempt; will be retried up to
     * {@code maxRetries} (application-configured).
     */
    FAILED,

    /**
     * Retry limit exceeded. Row is retained for audit / manual replay.
     * Operators should inspect {@code lastError} before replaying or discarding.
     */
    DEAD
}

