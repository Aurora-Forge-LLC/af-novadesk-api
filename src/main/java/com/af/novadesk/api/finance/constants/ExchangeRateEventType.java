package com.af.novadesk.api.finance.constants;

/**
 * Domain event types produced by the exchange-rate sync scheduler (LLR-FIN-02.3).
 *
 * <p>Each value maps to a specific state transition on an
 * {@link com.af.novadesk.api.finance.entity.ExchangeRate} record.
 * The event type drives consumer-side handler dispatch and payload
 * deserialization.</p>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<ExchangeRateEventType>:<rateDate>:<sourceCurrency>-><targetCurrency>"}
 */
public enum ExchangeRateEventType {

    /**
     * Raised when the daily exchange-rate sync scheduler successfully fetches
     * and persists a rate for a currency pair. Consumers use this event to
     * trigger downstream reconciliation, reporting pipelines, or notifications.
     */
    EXCHANGE_RATE_SYNC_COMPLETED,

    /**
     * Raised when the daily exchange-rate sync scheduler exhausts all retry
     * attempts for a currency pair. The event is written to the outbox with
     * {@code PENDING} status so operators can inspect the error and manually
     * trigger a replay.
     */
    EXCHANGE_RATE_SYNC_FAILED,

    /**
     * Reserved for future use — raised when a finance admin manually creates
     * or updates an exchange rate via the admin API endpoint.
     */
    EXCHANGE_RATE_MANUALLY_UPDATED
}
