-- =============================================================================
-- NOVADESK API - Add WRITE_OFF_PENDING to ast_assets status constraint
-- Version  : 1.113
-- Purpose  : Differentiates assets pending a DAMAGED/RETIRED/OTHER write-off
--            (WRITE_OFF_PENDING) from genuinely lost assets (LOST).
--            Previously all write-off requests set the asset status to LOST,
--            which was semantically incorrect for non-LOST reasons.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_assets
    DROP CONSTRAINT ck_ast_status;

ALTER TABLE af_novadesk.ast_assets
    ADD CONSTRAINT ck_ast_status
        CHECK (status IN ('AVAILABLE','ASSIGNED','RETURNED','LOST','WRITE_OFF_PENDING','DISPOSED','FULLY_DEPRECATED'));
