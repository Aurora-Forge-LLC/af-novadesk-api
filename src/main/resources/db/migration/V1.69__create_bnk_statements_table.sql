-- =============================================================================
-- LLR-BNK-01: Create bnk_statements table for uploaded bank statements
-- Schema: af_novadesk
-- =============================================================================

CREATE TABLE IF NOT EXISTS af_novadesk.bnk_statements (
    id                         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id            UUID         NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    bank_account_id            UUID         NOT NULL REFERENCES af_novadesk.entity_bank_accounts(id) ON DELETE RESTRICT,
    uploaded_by_shadow_user_id UUID         NOT NULL REFERENCES af_novadesk.shadow_users(id) ON DELETE RESTRICT,
    original_filename          VARCHAR(255) NOT NULL,
    storage_key                VARCHAR(500) NOT NULL,
    file_type                  VARCHAR(10)  NOT NULL,                               -- CSV, XLSX, PDF
    file_size_bytes            INTEGER      NOT NULL,
    is_encrypted               BOOLEAN      NOT NULL DEFAULT true,
    period_start               DATE         NOT NULL,
    period_end                 DATE         NOT NULL,
    transaction_count          INTEGER      NOT NULL DEFAULT 0,
    notes                      VARCHAR(500),
    statement_status           VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',            -- UPLOADED, PARSED, SUPERSEDED, FAILED
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',              -- soft-delete

    CONSTRAINT chk_bnk_statements_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_statements_statement_status CHECK (statement_status IN ('UPLOADED','PARSED','SUPERSEDED','FAILED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_bnk_statements_entity ON af_novadesk.bnk_statements (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_bank_account ON af_novadesk.bnk_statements (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_period ON af_novadesk.bnk_statements (bank_account_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_uploaded_by ON af_novadesk.bnk_statements (uploaded_by_shadow_user_id);

COMMENT ON TABLE af_novadesk.bnk_statements IS 'Uploaded bank statements for reconciliation';
COMMENT ON COLUMN af_novadesk.bnk_statements.statement_status IS 'Lifecycle: UPLOADED -> PARSED, or SUPERSEDED when replaced, FAILED when parsing fails';
COMMENT ON COLUMN af_novadesk.bnk_statements.storage_key IS 'MinIO/S3 object key: {orgId}/bank-statements/{entityId}/{uuid}.{ext}';
