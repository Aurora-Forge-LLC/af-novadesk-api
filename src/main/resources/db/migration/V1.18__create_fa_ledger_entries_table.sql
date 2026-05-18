-- =============================================================================
-- NOVADESK API - Create fa_ledger_entries Table
-- Version  : 1.15
-- Created  : 2026-05-18
-- Purpose  : Individual double-entry lines; append-only after creation.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fa_ledger_entries (
    id                 UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    journal_id         UUID           NOT NULL,
    transfer_id        UUID,
    legal_entity_id    UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    account_id         UUID           NOT NULL REFERENCES af_novadesk.fa_accounts(id) ON DELETE RESTRICT,
    entry_side         VARCHAR(10)    NOT NULL CHECK (entry_side IN ('DEBIT','CREDIT')),
    amount_local       DECIMAL(19,4)  NOT NULL CHECK (amount_local >= 0.01),
    currency_local     CHAR(3)        NOT NULL,
    amount_usd         DECIMAL(19,4)  NOT NULL CHECK (amount_usd >= 0.01),
    exchange_rate_used DECIMAL(19,6)  NOT NULL CHECK (exchange_rate_used > 0),
    rate_date_used     DATE           NOT NULL,
    description        VARCHAR(500),
    reference_type     VARCHAR(50)    NOT NULL,
    reference_id       UUID           NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fa_ledger_entries_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_journal
    ON af_novadesk.fa_ledger_entries (journal_id);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_entity_date
    ON af_novadesk.fa_ledger_entries (legal_entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_transfer
    ON af_novadesk.fa_ledger_entries (transfer_id)
    WHERE transfer_id IS NOT NULL;

COMMENT ON TABLE af_novadesk.fa_ledger_entries IS
    'Double-entry ledger lines produced by financial funding events.';

