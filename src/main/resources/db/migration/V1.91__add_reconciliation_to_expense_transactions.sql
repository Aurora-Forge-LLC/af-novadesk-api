-- LLR-BNK-02: Add reconciliation fields to exp_expense_transactions
ALTER TABLE af_novadesk.exp_expense_transactions
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(30) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS matched_bank_transaction_id UUID DEFAULT NULL;

-- FK constraint added later after bnk_transactions is stable
-- ALTER TABLE af_novadesk.exp_expense_transactions
--     ADD CONSTRAINT fk_et_matched_bank_txn FOREIGN KEY (matched_bank_transaction_id)
--         REFERENCES af_novadesk.bnk_transactions(id) ON DELETE SET NULL;

-- Index for matching queries
CREATE INDEX IF NOT EXISTS idx_exp_txn_reconciliation
    ON af_novadesk.exp_expense_transactions (legal_entity_id, reconciliation_status)
    WHERE reconciliation_status IS NULL AND transaction_status = 'POSTED';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.reconciliation_status IS
    'NULL=not applicable, UNRECONCILED, RECONCILED. Only set for bank-reconciled expenses.';
COMMENT ON COLUMN af_novadesk.exp_expense_transactions.matched_bank_transaction_id IS
    'FK to bnk_transactions when this expense was matched via bank reconciliation.';
