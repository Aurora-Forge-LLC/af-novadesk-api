-- =============================================================================
-- NOVADESK API - Ensure legacy outbox_event_status columns exist
-- Version  : 1.21
-- Created  : 2026-05-18
-- Purpose  : Forward-only safety migration for environments where V1.11 did not
--            apply successfully. Ensures Hibernate schema validation can pass.
-- =============================================================================

SET search_path TO af_novadesk_outbox;

-- entity_user_access_outbox_events
ALTER TABLE IF EXISTS af_novadesk_outbox.entity_user_access_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20);

UPDATE af_novadesk_outbox.entity_user_access_outbox_events
SET outbox_event_status = 'ACTIVE'
WHERE outbox_event_status IS NULL;

ALTER TABLE IF EXISTS af_novadesk_outbox.entity_user_access_outbox_events
    ALTER COLUMN outbox_event_status SET DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.entity_user_access_outbox_events
    ALTER COLUMN outbox_event_status SET NOT NULL;

-- legal_entity_outbox_events
ALTER TABLE IF EXISTS af_novadesk_outbox.legal_entity_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20);

UPDATE af_novadesk_outbox.legal_entity_outbox_events
SET outbox_event_status = 'ACTIVE'
WHERE outbox_event_status IS NULL;

ALTER TABLE IF EXISTS af_novadesk_outbox.legal_entity_outbox_events
    ALTER COLUMN outbox_event_status SET DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.legal_entity_outbox_events
    ALTER COLUMN outbox_event_status SET NOT NULL;

-- shadow_user_outbox_events
ALTER TABLE IF EXISTS af_novadesk_outbox.shadow_user_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20);

UPDATE af_novadesk_outbox.shadow_user_outbox_events
SET outbox_event_status = 'ACTIVE'
WHERE outbox_event_status IS NULL;

ALTER TABLE IF EXISTS af_novadesk_outbox.shadow_user_outbox_events
    ALTER COLUMN outbox_event_status SET DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.shadow_user_outbox_events
    ALTER COLUMN outbox_event_status SET NOT NULL;

