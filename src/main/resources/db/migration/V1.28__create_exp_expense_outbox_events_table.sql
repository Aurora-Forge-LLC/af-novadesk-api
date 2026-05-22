-- =============================================================================
-- NOVADESK API - Create exp_expense_outbox_events Table
-- Version  : 1.28
-- Created  : 2026-05-22
-- Purpose  : Transactional Outbox for the ExpenseTransaction aggregate.
--            Guarantees at-least-once event delivery without distributed
--            transactions (LLR-FIN-03).
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk_outbox.exp_expense_outbox_events (
    id                         UUID          NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    record_status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    aggregate_id               UUID          NOT NULL,
    event_type                 VARCHAR(50)   NOT NULL,
    payload                    JSONB         NOT NULL,
    organization_id            UUID          NOT NULL,
    triggered_by_auth_user_id  UUID,
    idempotency_key            VARCHAR(255)  NOT NULL,
    outbox_event_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    published_at               TIMESTAMPTZ,
    retry_count                INTEGER       NOT NULL DEFAULT 0,
    next_retry_at              TIMESTAMPTZ,
    last_error                 VARCHAR(1000),

    CONSTRAINT fk_exp_outbox_transaction
        FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.exp_expense_transactions(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_exp_outbox_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT chk_exp_outbox_event_status
        CHECK (outbox_event_status IN ('PENDING','PUBLISHED','FAILED','DEAD')),

    CONSTRAINT chk_exp_outbox_record_status
        CHECK (record_status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),

    CONSTRAINT chk_exp_outbox_event_type
        CHECK (event_type IN ('EXPENSE_CREATED','EXPENSE_VOIDED')),

    CONSTRAINT chk_exp_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_exp_outbox_status_created
    ON af_novadesk_outbox.exp_expense_outbox_events (outbox_event_status, created_at);

CREATE INDEX IF NOT EXISTS idx_exp_outbox_aggregate_id
    ON af_novadesk_outbox.exp_expense_outbox_events (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_exp_outbox_org_id
    ON af_novadesk_outbox.exp_expense_outbox_events (organization_id);

CREATE INDEX IF NOT EXISTS idx_exp_outbox_next_retry
    ON af_novadesk_outbox.exp_expense_outbox_events (outbox_event_status, next_retry_at)
    WHERE outbox_event_status IN ('PENDING', 'FAILED');

COMMENT ON TABLE af_novadesk_outbox.exp_expense_outbox_events IS
    'Transactional Outbox for the ExpenseTransaction aggregate (LLR-FIN-03). Ensures at-least-once delivery without distributed transactions.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.aggregate_id IS
    'FK to af_novadesk.exp_expense_transactions.id (source aggregate).';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.event_type IS
    'Domain event type: EXPENSE_CREATED, EXPENSE_VOIDED.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.payload IS
    'JSON-serialised event payload (jsonb). Schema documented in ExpenseEventType enum.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.organization_id IS
    'Tenant scope for routing and filtering — denormalised to avoid payload parsing.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.triggered_by_auth_user_id IS
    'JWT sub of the user who triggered the event. NULL for system-initiated events.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.idempotency_key IS
    'Convention: <ExpenseEventType>:<transactionId>:<requestTraceId>. Unique to prevent duplicate delivery on retry.';

COMMENT ON COLUMN af_novadesk_outbox.exp_expense_outbox_events.outbox_event_status IS
    'Delivery lifecycle: PENDING → PUBLISHED (success) or FAILED → DEAD (max retries exceeded).';
