-- =============================================================================
-- NOVADESK API - Create capital_injection_outbox_events Table
-- Version  : 1.20
-- Created  : 2026-05-18
-- Purpose  : Transactional outbox table for the CapitalInjection aggregate
--            (LLR-FIN-02) to guarantee at-least-once event delivery.
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk_outbox.capital_injection_outbox_events (
    id                        UUID         NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    created_at                TIMESTAMP    NOT NULL,
    updated_at                TIMESTAMP    NOT NULL,
    outbox_event_status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    aggregate_id              UUID         NOT NULL,
    event_type                VARCHAR(50)  NOT NULL,
    payload                   JSONB        NOT NULL,
    organization_id           UUID         NOT NULL,
    triggered_by_auth_user_id UUID,
    idempotency_key           VARCHAR(255) NOT NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    published_at              TIMESTAMP,
    retry_count               INTEGER      NOT NULL DEFAULT 0,
    next_retry_at             TIMESTAMP,
    last_error                VARCHAR(1000),
    CONSTRAINT fk_ci_outbox_capital_injection
        FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.fa_capital_injections(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_ci_outbox_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_ci_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD')),
    CONSTRAINT chk_ci_outbox_event_status
        CHECK (outbox_event_status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_ci_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ci_outbox_status_created
    ON af_novadesk_outbox.capital_injection_outbox_events (status, created_at);

CREATE INDEX IF NOT EXISTS idx_ci_outbox_aggregate_id
    ON af_novadesk_outbox.capital_injection_outbox_events (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_ci_outbox_org_id
    ON af_novadesk_outbox.capital_injection_outbox_events (organization_id);

CREATE INDEX IF NOT EXISTS idx_ci_outbox_next_retry
    ON af_novadesk_outbox.capital_injection_outbox_events (status, next_retry_at)
    WHERE status IN ('PENDING', 'FAILED');

COMMENT ON TABLE af_novadesk_outbox.capital_injection_outbox_events IS
    'Transactional Outbox for the CapitalInjection aggregate (LLR-FIN-02). Ensures at-least-once delivery without distributed transactions.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.aggregate_id IS
    'FK to af_novadesk.fa_capital_injections.id (source aggregate).';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.event_type IS
    'Domain event type: CAPITAL_INJECTION_CREATED, CAPITAL_INJECTION_REVERSED.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.payload IS
    'JSON-serialised event payload (jsonb).';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.organization_id IS
    'Tenant/legal-entity scope for routing and filtering.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.triggered_by_auth_user_id IS
    'JWT sub of the user who submitted the injection; NULL for system events.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.idempotency_key IS
    'Convention: <EventType>:<capitalInjectionId>:<journalId>; unique to avoid duplicates.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.status IS
    'Delivery state: PENDING, PUBLISHED, FAILED, DEAD.';

COMMENT ON COLUMN af_novadesk_outbox.capital_injection_outbox_events.outbox_event_status IS
    'Lifecycle status inherited from AbstractEntity; separate from delivery status.';

