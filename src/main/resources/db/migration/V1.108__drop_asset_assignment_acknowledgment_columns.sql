SET search_path TO af_novadesk;

-- The asset-acknowledgment feature (employee clicks a one-time email link to
-- confirm receipt) has been removed entirely. Drop its columns, constraint,
-- and index from ast_asset_assignments.

DROP INDEX IF EXISTS af_novadesk.idx_ast_asgn_ack_token;

ALTER TABLE af_novadesk.ast_asset_assignments
    DROP CONSTRAINT IF EXISTS ck_ast_asgn_ack_status;

ALTER TABLE af_novadesk.ast_asset_assignments
    DROP COLUMN IF EXISTS requires_acknowledgment,
    DROP COLUMN IF EXISTS acknowledgment_status,
    DROP COLUMN IF EXISTS acknowledgment_at,
    DROP COLUMN IF EXISTS acknowledgment_ip,
    DROP COLUMN IF EXISTS acknowledgment_token,
    DROP COLUMN IF EXISTS acknowledgment_token_expires_at;

COMMENT ON TABLE af_novadesk.ast_asset_assignments IS 'Asset assignment business records (LLR-AST-02).';
