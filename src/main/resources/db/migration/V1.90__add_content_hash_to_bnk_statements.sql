-- LLR-BNK-01.5: Add content_hash column for content-based deduplication
ALTER TABLE af_novadesk.bnk_statements
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);

-- Index for content-based duplicate lookups
CREATE INDEX IF NOT EXISTS idx_bnk_statements_content_hash
    ON af_novadesk.bnk_statements (bank_account_id, content_hash)
    WHERE status = 'ACTIVE' AND statement_status != 'SUPERSEDED';

COMMENT ON COLUMN af_novadesk.bnk_statements.content_hash IS 'SHA-256 hash of file content for deduplication';
