ALTER TABLE IF EXISTS af_novadesk_outbox.entity_user_access_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.legal_entity_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE IF EXISTS af_novadesk_outbox.shadow_user_outbox_events
    ADD COLUMN IF NOT EXISTS outbox_event_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
