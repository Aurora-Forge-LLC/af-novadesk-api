-- =============================================================================
-- LLR-BNK-01: Create bnk_transactions table for extracted bank transaction rows
-- Schema: af_novadesk
-- =============================================================================

CREATE TABLE IF NOT EXISTS af_novadesk.bnk_transactions (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id          UUID          NOT NULL REFERENCES af_novadesk.bnk_statements(id) ON DELETE CASCADE,
    legal_entity_id       UUID          NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    bank_account_id       UUID          NOT NULL REFERENCES af_novadesk.entity_bank_accounts(id) ON DELETE RESTRICT,
    transaction_date      DATE          NOT NULL,
    description           VARCHAR(500)  NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,                                    -- positive=credit(money in), negative=debit(money out)
    balance               DECIMAL(19,4),                                              -- running balance from statement
    reconciliation_status VARCHAR(30)   NOT NULL DEFAULT 'UNMATCHED',               -- UNMATCHED, SUGGESTED, MATCHED, IGNORED
    matched_ledger_entry_id UUID,                                                     -- FK to fa_ledger_entries, set after match
    matching_score        INTEGER,                                                    -- 0-100 from matching algorithm
    matching_method       VARCHAR(30),                                                -- AUTO_MATCHED, USER_CONFIRMED, MANUAL
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_transactions_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_transactions_reconciliation_status CHECK (reconciliation_status IN ('UNMATCHED','SUGGESTED','MATCHED','IGNORED')),
    CONSTRAINT chk_bnk_transactions_matching_method CHECK (matching_method IS NULL OR matching_method IN ('AUTO_MATCHED','USER_CONFIRMED','MANUAL'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_bnk_txn_statement ON af_novadesk.bnk_transactions (statement_id);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_entity ON af_novadesk.bnk_transactions (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_status ON af_novadesk.bnk_transactions (reconciliation_status);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_date ON af_novadesk.bnk_transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_amount ON af_novadesk.bnk_transactions (amount);

COMMENT ON TABLE af_novadesk.bnk_transactions IS 'Individual transactions extracted from bank statements';
COMMENT ON COLUMN af_novadesk.bnk_transactions.amount IS 'Positive = credit (money in), Negative = debit (money out)';
COMMENT ON COLUMN af_novadesk.bnk_transactions.reconciliation_status IS 'UNMATCHED -> SUGGESTED -> MATCHED, or IGNORED';
