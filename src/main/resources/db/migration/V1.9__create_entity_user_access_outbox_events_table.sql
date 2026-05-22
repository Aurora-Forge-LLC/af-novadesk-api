-- =============================================================================
-- NOVADESK API - Create entity_user_access_outbox_events Table
-- Version  : 1.9
-- Created  : 2026-05-15
-- Purpose  : Create the transactional outbox table for entity user access events.
--            Records access grant and access revoke events that must be delivered
--            promptly to enforce entity-scoped data isolation.
--
-- Table    : af_novadesk_outbox.entity_user_access_outbox_events
-- =============================================================================

-- Create entity_user_access_outbox_events table
CREATE TABLE IF NOT EXISTS af_novadesk_outbox.entity_user_access_outbox_events (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    organization_id UUID NOT NULL,
    affected_auth_user_id UUID NOT NULL,
    triggered_by_auth_user_id UUID,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_eua_outbox_entity_user_access FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.entity_user_accesses(id) ON DELETE CASCADE,
    CONSTRAINT uk_eua_outbox_idempotency_key UNIQUE (idempotency_key)
);

-- Create indexes for polling and security audit efficiency
CREATE INDEX IF NOT EXISTS idx_eua_outbox_status_created ON af_novadesk_outbox.entity_user_access_outbox_events (status, created_at);
CREATE INDEX IF NOT EXISTS idx_eua_outbox_aggregate_id ON af_novadesk_outbox.entity_user_access_outbox_events (aggregate_id);
CREATE INDEX IF NOT EXISTS idx_eua_outbox_org_id ON af_novadesk_outbox.entity_user_access_outbox_events (organization_id);
CREATE INDEX IF NOT EXISTS idx_eua_outbox_affected_user ON af_novadesk_outbox.entity_user_access_outbox_events (affected_auth_user_id);
CREATE INDEX IF NOT EXISTS idx_eua_outbox_next_retry ON af_novadesk_outbox.entity_user_access_outbox_events (status, next_retry_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- Add comments
COMMENT ON TABLE af_novadesk_outbox.entity_user_access_outbox_events IS 'Transactional outbox for EntityUserAccess aggregate - security-critical access change events';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.aggregate_id IS 'Reference to the entity user access record whose change produced this event';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.event_type IS 'Event type: USER_ACCESS_GRANTED, USER_ACCESS_REVOKED, USER_ROLE_CHANGED';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.payload IS 'JSON-serialised event payload containing access grant/revoke details';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.organization_id IS 'Organization context for filtering';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.affected_auth_user_id IS 'User whose access was granted, revoked, or changed (for security audits)';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.triggered_by_auth_user_id IS 'Admin who triggered the access change';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.idempotency_key IS 'Convention: <EventType>:<accessId>:<requestTraceId>';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.status IS 'Delivery state: PENDING, PUBLISHED, RETRYING, DEAD';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.published_at IS 'Timestamp of successful broker acknowledgement';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.retry_count IS 'Number of failed delivery attempts';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.next_retry_at IS 'Earliest time poller may attempt redelivery (exponential back-off)';
COMMENT ON COLUMN af_novadesk_outbox.entity_user_access_outbox_events.last_error IS 'Exception message or broker error from most recent failed attempt';
