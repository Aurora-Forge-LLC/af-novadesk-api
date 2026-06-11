-- LLR-BNK-01: Add reconciliation_status column to bnk_statements
ALTER TABLE af_novadesk.bnk_statements
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED';

-- Add check constraint for reconciliation_status values
ALTER TABLE af_novadesk.bnk_statements
    ADD CONSTRAINT chk_bnk_statements_reconciliation_status
        CHECK (reconciliation_status IN ('UNMATCHED', 'SUGGESTED', 'MATCHED', 'IGNORED'));

COMMENT ON COLUMN af_novadesk.bnk_statements.reconciliation_status IS 'Reconciliation state: UNMATCHED, SUGGESTED, MATCHED, or IGNORED';
