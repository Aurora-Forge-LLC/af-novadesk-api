-- =============================================================================
-- NOVADESK API - Create ast_outbox_events Table
-- Version  : 1.67
-- Created  : 2026-06-03
-- Purpose  : Transactional outbox for the Asset module domain events.
--            Events are written inside the same DB transaction as the business
--            operation (guarantees at-least-once delivery without distributed
--            transactions). A polling publisher transitions rows from PENDING
--            to PUBLISHED / FAILED / DEAD.
--
-- Events produced:
--   ASSET_PURCHASED          — triggers Fixed-Asset journal entry in finance
--   ASSET_DEPRECIATION_POSTED— triggers Depreciation Expense journal entry
--   ASSET_ASSIGNED           — audit / notification
--   ASSET_RETURNED           — audit / notification
--   ASSET_WRITTEN_OFF        — write-off requested
--   ASSET_WRITE_OFF_APPROVED — executive approved; asset → DISPOSED
--   ASSET_WRITE_OFF_REJECTED — executive rejected; asset reverted
-- =============================================================================

CREATE TABLE IF NOT EXISTS af_novadesk_outbox.ast_outbox_events (
    id                       UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),

    -- Aggregate reference (loose — no FK because events span multiple aggregate types)
    aggregate_id             UUID,
    aggregate_type           VARCHAR(50)     NOT NULL,

    -- Event routing
    event_type               VARCHAR(60)     NOT NULL,

    -- JSON payload (jsonb for indexed access and partial queries)
    payload                  JSONB           NOT NULL,

    -- Multi-tenancy context (denormalised — avoids payload parsing)
    organization_id          UUID,
    triggered_by_auth_user_id UUID,

    -- Idempotency
    idempotency_key          VARCHAR(255)    NOT NULL,

    -- Delivery lifecycle
    outbox_event_status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    published_at             TIMESTAMPTZ,
    retry_count              INT             NOT NULL DEFAULT 0,
    next_retry_at            TIMESTAMPTZ,
    last_error               VARCHAR(1000),

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    status                   VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT uk_ast_outbox_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_ast_outbox_status         CHECK (outbox_event_status IN ('PENDING','PUBLISHED','FAILED','DEAD'))
);

-- Poller's primary scan — pending rows ordered by arrival
CREATE INDEX idx_ast_outbox_status_created
    ON af_novadesk_outbox.ast_outbox_events (outbox_event_status, created_at)
    WHERE outbox_event_status = 'PENDING';

-- Retry polling — partial index on retryable states
CREATE INDEX idx_ast_outbox_next_retry
    ON af_novadesk_outbox.ast_outbox_events (outbox_event_status, next_retry_at)
    WHERE outbox_event_status IN ('PENDING', 'FAILED');

-- Aggregate lookup (debugging / replaying events for a specific asset)
CREATE INDEX idx_ast_outbox_aggregate
    ON af_novadesk_outbox.ast_outbox_events (aggregate_id)
    WHERE aggregate_id IS NOT NULL;

-- Org-scoped queries for ops tooling
CREATE INDEX idx_ast_outbox_org
    ON af_novadesk_outbox.ast_outbox_events (organization_id);

COMMENT ON TABLE  af_novadesk_outbox.ast_outbox_events IS 'Transactional outbox for Asset module domain events.';
COMMENT ON COLUMN af_novadesk_outbox.ast_outbox_events.aggregate_id   IS 'UUID of the source aggregate (Asset, AssetAssignment, AssetWriteOff). Loose reference — no FK.';
COMMENT ON COLUMN af_novadesk_outbox.ast_outbox_events.aggregate_type IS 'Class name of the source aggregate, e.g. ASSET, ASSET_ASSIGNMENT, ASSET_WRITE_OFF.';
COMMENT ON COLUMN af_novadesk_outbox.ast_outbox_events.idempotency_key IS 'Producer-assigned key: <EventType>:<aggregateId>:<UUID>. Prevents duplicate insertion on retry.';
COMMENT ON COLUMN af_novadesk_outbox.ast_outbox_events.outbox_event_status IS 'PENDING → PUBLISHED on success; PENDING/FAILED → DEAD after maxRetries.';
