-- =============================================================================
-- NOVADESK API - Create exp_expense_transactions Table
-- Version  : 1.28
-- Created  : 2026-05-22
-- Purpose  : Header record for each manually-recorded expense.
--            Each saved row produces exactly two ledger entries in
--            fa_ledger_entries (one CREDIT, one DEBIT). (LLR-FIN-03.2)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.exp_expense_transactions (
    id                         UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id            UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)  ON DELETE RESTRICT,
    vendor_id                  UUID           NOT NULL REFERENCES af_novadesk.exp_vendors(id)      ON DELETE RESTRICT,
    created_by_shadow_user_id  UUID           NOT NULL REFERENCES af_novadesk.shadow_users(id)     ON DELETE RESTRICT,
    expense_date               DATE           NOT NULL,
    amount                     DECIMAL(19,4)  NOT NULL CHECK (amount >= 0.01),
    currency_code              CHAR(3)        NOT NULL,
    payment_method             VARCHAR(20)    NOT NULL,
    source_account_id          UUID           NOT NULL REFERENCES af_novadesk.fa_accounts(id)      ON DELETE RESTRICT,
    destination_account_id     UUID           NOT NULL REFERENCES af_novadesk.fa_accounts(id)      ON DELETE RESTRICT,
    invoice_receipt_number     VARCHAR(50),
    description                VARCHAR(500)   NOT NULL,
    transaction_status         VARCHAR(20)    NOT NULL DEFAULT 'POSTED',
    status                     VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_exp_txn_payment_method
        CHECK (payment_method IN ('BANK_TRANSFER','CREDIT_CARD','CASH','CHECK')),

    CONSTRAINT chk_exp_txn_transaction_status
        CHECK (transaction_status IN ('POSTED','VOID')),

    CONSTRAINT chk_exp_txn_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),

    CONSTRAINT chk_exp_txn_diff_accounts
        CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX IF NOT EXISTS idx_exp_txn_entity_date
    ON af_novadesk.exp_expense_transactions (legal_entity_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_exp_txn_vendor_id
    ON af_novadesk.exp_expense_transactions (vendor_id);

CREATE INDEX IF NOT EXISTS idx_exp_txn_created_by
    ON af_novadesk.exp_expense_transactions (created_by_shadow_user_id);

COMMENT ON TABLE af_novadesk.exp_expense_transactions IS
    'Manual expense header record (LLR-FIN-03). Each row produces exactly two fa_ledger_entries rows (CREDIT on source, DEBIT on destination).';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.source_account_id IS
    'Account being CREDITED — funds leave this account (e.g. Company Bank Account).';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.destination_account_id IS
    'Account being DEBITED — expense is recognised here (e.g. Cloud Infrastructure Expense).';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.transaction_status IS
    'Accounting lifecycle: POSTED (active) or VOID (reversed). Separate from the soft-delete status column.';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.currency_code IS
    'ISO 4217 code stored explicitly from the entity base currency at submission time for historical accuracy.';
