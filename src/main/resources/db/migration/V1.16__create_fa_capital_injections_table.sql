-- =============================================================================
-- NOVADESK API - Create fa_capital_injections Table
-- Version  : 1.14
-- Created  : 2026-05-18
-- Purpose  : Header record for each capital injection event.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fa_capital_injections (
    id                     UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    transfer_id            UUID,
    target_legal_entity_id UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    source_legal_entity_id UUID           REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    funding_source         VARCHAR(30)    NOT NULL,
    funding_date           DATE           NOT NULL,
    amount_local           DECIMAL(19,4)  NOT NULL CHECK (amount_local >= 0.01),
    currency_local         CHAR(3)        NOT NULL,
    amount_usd             DECIMAL(19,4)  NOT NULL CHECK (amount_usd >= 0.01),
    exchange_rate_used     DECIMAL(19,6)  NOT NULL CHECK (exchange_rate_used > 0),
    rate_date_used         DATE           NOT NULL,
    rate_source            VARCHAR(30)    NOT NULL,
    source_account_id      UUID           NOT NULL REFERENCES af_novadesk.fa_accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID           NOT NULL REFERENCES af_novadesk.fa_accounts(id) ON DELETE RESTRICT,
    reference_number       VARCHAR(50),
    notes                  VARCHAR(500),
    created_by             VARCHAR(100),
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fa_capital_injections_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_capital_injections_entity_date
    ON af_novadesk.fa_capital_injections (target_legal_entity_id, funding_date DESC);

CREATE INDEX IF NOT EXISTS idx_fa_capital_injections_transfer
    ON af_novadesk.fa_capital_injections (transfer_id)
    WHERE transfer_id IS NOT NULL;

COMMENT ON TABLE af_novadesk.fa_capital_injections IS
    'Capital injection header record, referenced by ledger entries and outbox events.';

