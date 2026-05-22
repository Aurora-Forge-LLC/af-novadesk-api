-- =============================================================================
-- NOVADESK API - Create exchange_rate_outbox_events Table
-- Version  : 1.27
-- Created  : 2026-05-22
-- Purpose  : Transactional outbox table for the ExchangeRate aggregate
--            (LLR-FIN-02.3) to guarantee at-least-once event delivery.
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk_outbox.exchange_rate_outbox_events (
    id                        UUID         NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    created_at                TIMESTAMP    NOT NULL,
    updated_at                TIMESTAMP    NOT NULL,
    outbox_event_status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    aggregate_id              UUID,
    event_type                VARCHAR(50)  NOT NULL,
    payload                   JSONB        NOT NULL,
    organization_id           UUID,
    triggered_by_auth_user_id UUID,
    idempotency_key           VARCHAR(255) NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    published_at              TIMESTAMPTZ,
    retry_count               INTEGER      NOT NULL DEFAULT 0,
    next_retry_at             TIMESTAMPTZ,
    last_error                VARCHAR(1000),
    CONSTRAINT fk_er_outbox_exchange_rate
        FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.fa_exchange_rates(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_er_outbox_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_er_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD')),
    CONSTRAINT chk_er_outbox_event_status
        CHECK (outbox_event_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_er_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_er_outbox_status_created
    ON af_novadesk_outbox.exchange_rate_outbox_events (status, created_at);

CREATE INDEX IF NOT EXISTS idx_er_outbox_aggregate_id
    ON af_novadesk_outbox.exchange_rate_outbox_events (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_er_outbox_org_id
    ON af_novadesk_outbox.exchange_rate_outbox_events (organization_id);

CREATE INDEX IF NOT EXISTS idx_er_outbox_next_retry
    ON af_novadesk_outbox.exchange_rate_outbox_events (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

COMMENT ON TABLE af_novadesk_outbox.exchange_rate_outbox_events IS
    'Transactional Outbox for the ExchangeRate aggregate (LLR-FIN-02.3). Ensures at-least-once delivery without distributed transactions.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.aggregate_id IS
    'FK to af_novadesk.fa_exchange_rates.id (source aggregate); nullable for failure events without a persisted rate.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.event_type IS
    'Domain event type: EXCHANGE_RATE_SYNC_COMPLETED, EXCHANGE_RATE_SYNC_FAILED, EXCHANGE_RATE_MANUALLY_UPDATED.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.payload IS
    'JSON-serialised event payload (jsonb).';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.organization_id IS
    'Tenant/legal-entity scope for routing and filtering.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.triggered_by_auth_user_id IS
    'JWT sub of the user who triggered the event; NULL for scheduler-initiated events.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.idempotency_key IS
    'Convention: <ExchangeRateEventType>:<rateDate>:<sourceCurrency>-><targetCurrency>; unique to avoid duplicates.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.status IS
    'Delivery state: PENDING, PUBLISHED, FAILED, DEAD.';

COMMENT ON COLUMN af_novadesk_outbox.exchange_rate_outbox_events.outbox_event_status IS
    'Lifecycle status inherited from AbstractEntity; separate from delivery status.';
