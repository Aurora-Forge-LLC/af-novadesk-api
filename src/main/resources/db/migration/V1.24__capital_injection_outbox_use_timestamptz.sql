-- =============================================================================
-- NOVADESK API - Capital Injection Outbox Timestamp Zone Hardening
-- Version  : 1.23
-- Created  : 2026-05-18
-- Purpose  : Make outbox publication/retry timestamps timezone-safe by moving
--            from TIMESTAMP to TIMESTAMPTZ.
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

-- Treat legacy timestamp values as UTC while converting to TIMESTAMPTZ.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'af_novadesk_outbox'
          AND table_name = 'capital_injection_outbox_events'
          AND column_name = 'published_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE af_novadesk_outbox.capital_injection_outbox_events
            ALTER COLUMN published_at TYPE TIMESTAMPTZ USING published_at AT TIME ZONE 'UTC';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'af_novadesk_outbox'
          AND table_name = 'capital_injection_outbox_events'
          AND column_name = 'next_retry_at'
          AND data_type = 'timestamp without time zone'
    ) THEN
        ALTER TABLE af_novadesk_outbox.capital_injection_outbox_events
            ALTER COLUMN next_retry_at TYPE TIMESTAMPTZ USING next_retry_at AT TIME ZONE 'UTC';
    END IF;
END $$;


