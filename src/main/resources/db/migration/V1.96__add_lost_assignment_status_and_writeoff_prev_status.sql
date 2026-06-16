-- =============================================================================
-- NOVADESK API - Add LOST assignment status + write-off previous asset status
-- Version  : 1.96
-- Purpose  : When a write-off is requested while an asset is ASSIGNED, the
--            active assignment is marked LOST so it surfaces in employee /
--            offboarding views. ast_write_offs gains previous_asset_status so
--            a rejected write-off can restore the asset to its prior state
--            (ASSIGNED or RETURNED) instead of always reverting to ASSIGNED.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_asset_assignments
    DROP CONSTRAINT ck_ast_asgn_status;

ALTER TABLE af_novadesk.ast_asset_assignments
    ADD CONSTRAINT ck_ast_asgn_status CHECK (status IN ('ACTIVE','RETURNED','TRANSFERRED','LOST'));

ALTER TABLE af_novadesk.ast_write_offs
    ADD COLUMN previous_asset_status VARCHAR(20);

UPDATE af_novadesk.ast_write_offs
SET previous_asset_status = 'ASSIGNED'
WHERE previous_asset_status IS NULL;

ALTER TABLE af_novadesk.ast_write_offs
    ALTER COLUMN previous_asset_status SET NOT NULL;

ALTER TABLE af_novadesk.ast_write_offs
    ADD CONSTRAINT ck_ast_wo_prev_status CHECK (previous_asset_status IN ('AVAILABLE','ASSIGNED','RETURNED','LOST','DISPOSED','FULLY_DEPRECATED'));

COMMENT ON COLUMN af_novadesk.ast_write_offs.previous_asset_status IS 'Asset status before the write-off request — restored if the write-off is rejected.';
