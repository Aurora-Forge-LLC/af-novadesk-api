package com.af.novadesk.api.finance.constants;

/**
 * Domain event types produced by the capital-injection aggregate (LLR-FIN-02).
 *
 * <p>Each value maps to a specific state transition on a {@link
 * com.af.novadesk.api.finance.entity.CapitalInjection} record.
 * The event type drives consumer-side handler dispatch and payload
 * deserialization.</p>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<CapitalInjectionEventType>:<capitalInjectionId>:<journalId>"}
 */
public enum CapitalInjectionEventType {

    /**
     * Raised when a new capital injection (and its double-entry ledger lines)
     * has been successfully persisted within a single atomic transaction.
     * Consumers use this event to trigger downstream reconciliation, reporting
     * pipelines, or notifications.
     */
    CAPITAL_INJECTION_CREATED,

    /**
     * Reserved for future use — raised when a capital injection is reversed
     * (e.g. due to a bank return or erroneous entry).
     * Consumers must issue compensating ledger entries upon receipt.
     */
    CAPITAL_INJECTION_REVERSED
}

