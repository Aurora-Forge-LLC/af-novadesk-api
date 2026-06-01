-- =============================================================================
-- NOVADESK API - Create pr_payroll_ledger_entries Table
-- Version  : 1.45
-- Created  : 2026-06-01
-- Purpose  : Payroll-specific immutable double-entry ledger. NOT shared with
--            the finance module's fa_ledger_entries table. Created when a
--            payroll batch is approved. Supports reversal entries for voided
--            batches (is_reversal=true, original_entry_id links back).
--            (LLR-PAY-03)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payroll_ledger_entries (
    id                  UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    journal_id          UUID           NOT NULL,
    payroll_batch_id    UUID           NOT NULL REFERENCES af_novadesk.pr_payroll_batches(id)  ON DELETE CASCADE,
    legal_entity_id     UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)       ON DELETE RESTRICT,
    account_code        VARCHAR(50)    NOT NULL,
    account_description VARCHAR(200)   NOT NULL,
    entry_side          VARCHAR(10)    NOT NULL,
    amount              DECIMAL(19,4)  NOT NULL CHECK (amount >= 0),
    currency_code       CHAR(3)        NOT NULL,
    amount_usd          DECIMAL(19,4),
    exchange_rate_used  DECIMAL(19,6),
    is_reversal         BOOLEAN        NOT NULL DEFAULT FALSE,
    original_entry_id   UUID,
    description         VARCHAR(500),
    reference_type      VARCHAR(50)    NOT NULL DEFAULT 'PAYROLL',
    reference_id        UUID           NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ple_entry_side
        CHECK (entry_side IN ('DEBIT','CREDIT')),
    CONSTRAINT chk_ple_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ple_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_ple_journal_side
    ON af_novadesk.pr_payroll_ledger_entries (journal_id, entry_side);

CREATE INDEX IF NOT EXISTS idx_ple_entity_date
    ON af_novadesk.pr_payroll_ledger_entries (legal_entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ple_batch_id
    ON af_novadesk.pr_payroll_ledger_entries (payroll_batch_id);

COMMENT ON TABLE af_novadesk.pr_payroll_ledger_entries IS
    'Payroll-specific double-entry ledger (LLR-PAY-03). Not shared with finance module.';

COMMENT ON COLUMN af_novadesk.pr_payroll_ledger_entries.reference_type IS
    'Discriminator for future ledger consolidation: "PAYROLL" identifies payroll entries.';
