-- =============================================================================
-- NOVADESK API - Convert ast_write_offs.reason to enum-backed column
-- Version  : 1.95
-- Purpose  : Replace the free-text write-off reason with a fixed set of
--            asset-related reasons (DAMAGED, LOST, RETIRED, OTHER).
-- =============================================================================

SET search_path TO af_novadesk;

-- Normalize existing free-text reasons to the new enum values before
-- shrinking the column — old rows hold arbitrary-length descriptions that
-- cannot be mapped automatically, so fall back to OTHER.
UPDATE af_novadesk.ast_write_offs
SET reason = 'OTHER'
WHERE reason NOT IN ('DAMAGED', 'LOST', 'RETIRED', 'OTHER');

ALTER TABLE af_novadesk.ast_write_offs
    ALTER COLUMN reason TYPE VARCHAR(20);

ALTER TABLE af_novadesk.ast_write_offs
    ADD CONSTRAINT ck_ast_wo_reason CHECK (reason IN ('DAMAGED', 'LOST', 'RETIRED', 'OTHER'));

COMMENT ON COLUMN af_novadesk.ast_write_offs.reason IS 'Asset-related write-off reason: DAMAGED, LOST, RETIRED, or OTHER.';
