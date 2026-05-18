-- =============================================================================
-- NOVADESK API - Create fa_exchange_rates Table
-- Version  : 1.13
-- Created  : 2026-05-18
-- Purpose  : Daily FX snapshot for each currency pair used by funding entries.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fa_exchange_rates (
    id              UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    source_currency CHAR(3)        NOT NULL,
    target_currency CHAR(3)        NOT NULL,
    rate_date       DATE           NOT NULL,
    exchange_rate   DECIMAL(19,6)  NOT NULL CHECK (exchange_rate > 0),
    rate_source     VARCHAR(30)    NOT NULL,
    created_by      VARCHAR(100),
    approved_by     VARCHAR(100),
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fa_exchange_rate UNIQUE (source_currency, target_currency, rate_date),
    CONSTRAINT chk_fa_exchange_rates_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_exchange_rate_lookup
    ON af_novadesk.fa_exchange_rates (source_currency, target_currency, rate_date DESC);

COMMENT ON TABLE af_novadesk.fa_exchange_rates IS
    'Daily FX rate snapshot used to convert local currency amounts into USD.';

