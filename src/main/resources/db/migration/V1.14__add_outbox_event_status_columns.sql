-- =============================================================================
-- NOVADESK API - Backfill outbox_event_status columns
-- Version  : 1.13
-- Created  : 2026-05-18
-- Purpose  : Align outbox table structure with JPA mappings that override
--            AbstractEntity.status -> outbox_event_status.
--
-- Why this migration exists:
--   Outbox entities inherit AbstractEntity.status and map it to the
--   column outbox_event_status via @AttributeOverride, while delivery-state
--   remains in the existing status column (PENDING/RETRYING/etc.).
--
--   Existing outbox tables were created without outbox_event_status, which
--   causes Hibernate schema validation failure at startup.
-- =============================================================================

SET search_path TO af_novadesk, af_novadesk_outbox, public;

ALTER TABLE IF EXISTS af_novadesk_outbox.legal_entity_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.entity_user_access_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.shadow_user_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

