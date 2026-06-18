-- =============================================================================
-- NOVADESK API - Unify finance and payroll ledger tables
-- Version  : 1.98
-- Created  : 2026-06-16
-- Purpose  : Replace fa_ledger_entries (finance) and pr_payroll_ledger_entries
--            (payroll) with a single ledger_entries table.  Account data is
--            snapshotted as strings at posting time — no FK to fa_accounts or
--            chart_of_accounts — so the ledger remains accurate after account
--            renames or deletions.
-- =============================================================================

SET search_path TO af_novadesk;

-- ─── 1. Create unified table ─────────────────────────────────────────────────

CREATE TABLE af_novadesk.ledger_entries (
    id                  UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    journal_id          UUID           NOT NULL,
    transfer_id         UUID,
    legal_entity_id     UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    module              VARCHAR(30)    NOT NULL,
    account_code        VARCHAR(50)    NOT NULL,
    account_name        VARCHAR(200)   NOT NULL,
    entry_side          VARCHAR(10)    NOT NULL CHECK (entry_side IN ('DEBIT','CREDIT')),
    amount_local        DECIMAL(19,4)  NOT NULL CHECK (amount_local >= 0.01),
    currency_local      CHAR(3)        NOT NULL,
    amount_usd          DECIMAL(19,4),
    exchange_rate_used  DECIMAL(19,6),
    rate_date_used      DATE,
    rate_warning        BOOLEAN        NOT NULL DEFAULT FALSE,
    is_reversal         BOOLEAN        NOT NULL DEFAULT FALSE,
    original_entry_id   UUID,
    description         VARCHAR(500),
    reference_type      VARCHAR(50)    NOT NULL,
    reference_id        UUID           NOT NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_le_module   CHECK (module IN ('EXPENSE','PAYROLL','CAPITAL_INJECTION','ASSET')),
    CONSTRAINT chk_le_status   CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

COMMENT ON TABLE af_novadesk.ledger_entries IS
    'Unified double-entry ledger shared by finance and payroll modules. '
    'account_code/account_name are snapshots at posting time; no live FK to account tables.';

-- ─── 2. Migrate finance entries (fa_ledger_entries) ──────────────────────────
--
-- Each row has EITHER account_id (fa_accounts) OR chart_of_account_id (chart_of_accounts).
-- COALESCE picks whichever is non-null for code/name lookup.

INSERT INTO af_novadesk.ledger_entries (
    id, journal_id, transfer_id, legal_entity_id,
    module,
    account_code, account_name,
    entry_side, amount_local, currency_local,
    amount_usd, exchange_rate_used, rate_date_used, rate_warning,
    is_reversal, original_entry_id,
    description, reference_type, reference_id,
    status, created_at, updated_at
)
SELECT
    fle.id,
    fle.journal_id,
    fle.transfer_id,
    fle.legal_entity_id,
    CASE fle.reference_type
        WHEN 'CAPITAL_INJECTION' THEN 'CAPITAL_INJECTION'
        ELSE 'EXPENSE'
    END AS module,
    COALESCE(fa.account_code, coa.account_code)  AS account_code,
    COALESCE(fa.account_name, coa.account_name)  AS account_name,
    fle.entry_side,
    fle.amount_local,
    fle.currency_local,
    fle.amount_usd,
    fle.exchange_rate_used,
    fle.rate_date_used,
    COALESCE(fle.rate_warning, FALSE),
    FALSE   AS is_reversal,
    NULL    AS original_entry_id,
    fle.description,
    fle.reference_type,
    fle.reference_id,
    fle.status,
    fle.created_at,
    fle.updated_at
FROM af_novadesk.fa_ledger_entries fle
LEFT JOIN af_novadesk.fa_accounts        fa  ON fa.id  = fle.account_id
LEFT JOIN af_novadesk.chart_of_accounts  coa ON coa.id = fle.chart_of_account_id;

-- ─── 3. Migrate payroll entries (pr_payroll_ledger_entries) ──────────────────

INSERT INTO af_novadesk.ledger_entries (
    id, journal_id, transfer_id, legal_entity_id,
    module,
    account_code, account_name,
    entry_side, amount_local, currency_local,
    amount_usd, exchange_rate_used, rate_date_used, rate_warning,
    is_reversal, original_entry_id,
    description, reference_type, reference_id,
    status, created_at, updated_at
)
SELECT
    ple.id,
    ple.journal_id,
    NULL                       AS transfer_id,
    ple.legal_entity_id,
    'PAYROLL'                  AS module,
    ple.account_code,
    ple.account_description    AS account_name,
    ple.entry_side,
    ple.amount                 AS amount_local,
    ple.currency_code          AS currency_local,
    ple.amount_usd,
    ple.exchange_rate_used,
    NULL                       AS rate_date_used,
    FALSE                      AS rate_warning,
    ple.is_reversal,
    ple.original_entry_id,
    ple.description,
    ple.reference_type,
    ple.reference_id,
    ple.status,
    ple.created_at,
    ple.updated_at
FROM af_novadesk.pr_payroll_ledger_entries ple;

-- ─── 4. Indexes ──────────────────────────────────────────────────────────────

CREATE INDEX idx_le_journal_id   ON af_novadesk.ledger_entries (journal_id);
CREATE INDEX idx_le_entity_date  ON af_novadesk.ledger_entries (legal_entity_id, created_at DESC);
CREATE INDEX idx_le_reference    ON af_novadesk.ledger_entries (reference_type, reference_id);
CREATE INDEX idx_le_module       ON af_novadesk.ledger_entries (module);
CREATE INDEX idx_le_account_code ON af_novadesk.ledger_entries (legal_entity_id, account_code);
CREATE INDEX idx_le_transfer_id  ON af_novadesk.ledger_entries (transfer_id) WHERE transfer_id IS NOT NULL;

-- ─── 5. Drop old tables ───────────────────────────────────────────────────────
-- pr_payroll_ledger_entries has no dependents; fa_ledger_entries has no dependents.
-- The XOR check constraint on fa_ledger_entries and all old indexes are dropped
-- implicitly when the table is dropped.

ALTER TABLE af_novadesk.pr_payroll_ledger_entries
    DROP CONSTRAINT IF EXISTS fk_ple_batch;   -- removes FK so table can be dropped cleanly

DROP TABLE af_novadesk.pr_payroll_ledger_entries;
DROP TABLE af_novadesk.fa_ledger_entries;
