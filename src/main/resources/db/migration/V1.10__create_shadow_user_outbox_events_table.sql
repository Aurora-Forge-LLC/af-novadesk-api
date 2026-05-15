-- =============================================================================
-- NOVADESK API - Create shadow_user_outbox_events Table
-- Version  : 1.10
-- Created  : 2026-05-15
-- Purpose  : Create the transactional outbox table for shadow user events.
--            Records user identity updates that must be synced across Finance
--            sub-modules to maintain consistent identity projections.
--
-- Table    : af_novadesk.shadow_user_outbox_events
-- =============================================================================

-- Create shadow_user_outbox_events table
CREATE TABLE af_novadesk.shadow_user_outbox_events (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    organization_id UUID NOT NULL,
    triggered_by_auth_user_id UUID,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_su_outbox_shadow_user FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.shadow_users(id) ON DELETE CASCADE,
    CONSTRAINT uk_su_outbox_idempotency_key UNIQUE (idempotency_key)
);

-- Create indexes for polling efficiency
CREATE INDEX idx_su_outbox_status_created ON af_novadesk.shadow_user_outbox_events (status, created_at);
CREATE INDEX idx_su_outbox_aggregate_id ON af_novadesk.shadow_user_outbox_events (aggregate_id);
CREATE INDEX idx_su_outbox_org_id ON af_novadesk.shadow_user_outbox_events (organization_id);
CREATE INDEX idx_su_outbox_next_retry ON af_novadesk.shadow_user_outbox_events (status, next_retry_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- Add comments
COMMENT ON TABLE af_novadesk.shadow_user_outbox_events IS 'Transactional outbox for ShadowUser aggregate - identity projection sync events';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.aggregate_id IS 'Reference to the shadow user whose identity was updated';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.event_type IS 'Event type: USER_CREATED, USER_UPDATED, USER_SYNCED';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.payload IS 'JSON-serialised event payload containing updated user identity data';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.organization_id IS 'Organization context for filtering';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.triggered_by_auth_user_id IS 'JWT sub (same as shadowUser.authUserId), denormalised for routing efficiency';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.idempotency_key IS 'Convention: <EventType>:<shadowUserId>:<requestTraceId>';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.status IS 'Delivery state: PENDING, PUBLISHED, RETRYING, DEAD';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.published_at IS 'Timestamp of successful broker acknowledgement';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.retry_count IS 'Number of failed delivery attempts';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.next_retry_at IS 'Earliest time poller may attempt redelivery (exponential back-off)';
COMMENT ON COLUMN af_novadesk.shadow_user_outbox_events.last_error IS 'Exception message or broker error from most recent failed attempt';

