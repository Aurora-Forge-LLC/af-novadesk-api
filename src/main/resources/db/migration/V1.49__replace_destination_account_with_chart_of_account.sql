-- V1.49 — Replace destination_account_id with chart_of_account_id on expense transactions
--          and add chart_of_account_id (nullable) to ledger entries for expense DEBIT entries.
--
-- Background:
--   Previously, expense transactions pointed to two fa_accounts entries (source + destination).
--   The destination account is now replaced by a chart_of_accounts entry so that expenses are
--   properly categorised (e.g. Rent Expense 5200, Salaries 5100) rather than against an
--   operational funding account.
--
--   Ledger entries: the CREDIT side still references fa_accounts (account_id).
--   The DEBIT side now references chart_of_accounts (chart_of_account_id).
--   account_id is made nullable to accommodate expense DEBIT entries.

-- ── Safety guard ─────────────────────────────────────────────────────────────
-- Refuse to run on a database that already has production expense data.
-- This migration deletes all expense rows and cannot be safely rolled back;
-- on any environment with real data, back up and migrate manually first.

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM af_novadesk.exp_expense_transactions) > 0 THEN
        RAISE EXCEPTION
            'V1.49 safety guard: exp_expense_transactions is not empty (% rows found). '
            'This migration will permanently delete all expense data. '
            'Back up and manually migrate expense rows before running on this environment.',
            (SELECT COUNT(*) FROM af_novadesk.exp_expense_transactions);
    END IF;
END $$;

-- ── Clean up existing expense data ───────────────────────────────────────────
-- Existing expense transactions reference destination_account_id (fa_accounts).
-- These cannot be backfilled to chart_of_account_id (chart_of_accounts) as the
-- FK targets a different table. All existing expense rows are test/dev data and
-- are removed before the schema change so the NOT NULL constraint can be applied.

DELETE FROM af_novadesk.fa_ledger_entries
    WHERE reference_type = 'EXPENSE';

DELETE FROM af_novadesk.exp_expense_attachments;

DELETE FROM af_novadesk.exp_expense_transactions;

-- ── exp_expense_transactions ──────────────────────────────────────────────────

-- Drop the old CHECK constraint that enforced source <> destination
ALTER TABLE af_novadesk.exp_expense_transactions
    DROP CONSTRAINT IF EXISTS exp_expense_transactions_source_destination_check;

-- Drop the foreign key and column for destination_account_id
ALTER TABLE af_novadesk.exp_expense_transactions
    DROP CONSTRAINT IF EXISTS fk_et_destination_account;

ALTER TABLE af_novadesk.exp_expense_transactions
    DROP COLUMN IF EXISTS destination_account_id;

-- Add chart_of_account_id FK → af_novadesk.chart_of_accounts
ALTER TABLE af_novadesk.exp_expense_transactions
    ADD COLUMN chart_of_account_id UUID NOT NULL
        CONSTRAINT fk_et_chart_of_account
        REFERENCES af_novadesk.chart_of_accounts(id) ON DELETE RESTRICT;

CREATE INDEX idx_exp_txn_chart_of_account
    ON af_novadesk.exp_expense_transactions(chart_of_account_id);

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.chart_of_account_id IS
    'Chart of Accounts entry being DEBITED — the expense category (e.g. Rent Expense, Salaries). '
    'Must belong to the same legal entity.';

-- ── fa_ledger_entries ─────────────────────────────────────────────────────────

-- Make account_id nullable — expense DEBIT entries will have account_id = NULL
--   and use chart_of_account_id instead.
ALTER TABLE af_novadesk.fa_ledger_entries
    ALTER COLUMN account_id DROP NOT NULL;

-- Add chart_of_account_id FK (nullable — only populated for expense DEBIT entries)
ALTER TABLE af_novadesk.fa_ledger_entries
    ADD COLUMN IF NOT EXISTS chart_of_account_id UUID
        CONSTRAINT fk_le_chart_of_account
        REFERENCES af_novadesk.chart_of_accounts(id) ON DELETE RESTRICT;

COMMENT ON COLUMN af_novadesk.fa_ledger_entries.account_id IS
    'fa_accounts entry — populated for capital injection entries and expense CREDIT entries. '
    'NULL for expense DEBIT entries (which use chart_of_account_id instead).';

COMMENT ON COLUMN af_novadesk.fa_ledger_entries.chart_of_account_id IS
    'Chart of Accounts entry — populated only for expense DEBIT (and void CREDIT reversal) entries. '
    'NULL for capital injection entries and expense CREDIT entries.';

-- XOR constraint: exactly one of account_id / chart_of_account_id must be set per row.
-- Prevents corrupt rows where both are null or both are non-null — including writes
-- that bypass the service layer (direct SQL, migrations, bulk imports).
ALTER TABLE af_novadesk.fa_ledger_entries
    ADD CONSTRAINT ck_le_account_xor
    CHECK (
        (account_id IS NOT NULL AND chart_of_account_id IS NULL) OR
        (account_id IS NULL     AND chart_of_account_id IS NOT NULL)
    );

-- Index to support JOIN / filter on chart_of_account_id (expense DEBIT lookups).
CREATE INDEX IF NOT EXISTS idx_le_chart_of_account
    ON af_novadesk.fa_ledger_entries (chart_of_account_id)
    WHERE chart_of_account_id IS NOT NULL;
