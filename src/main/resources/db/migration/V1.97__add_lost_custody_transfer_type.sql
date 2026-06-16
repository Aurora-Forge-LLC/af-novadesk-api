-- =============================================================================
-- NOVADESK API - Add LOST custody transfer type
-- Version  : 1.97
-- Purpose  : When a write-off is requested, a custody transfer row is recorded
--            so the chain-of-custody history reflects that the asset was
--            reported lost (in addition to the eventual WRITE_OFF/DISPOSAL
--            transfer on approval).
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_custody_transfers
    DROP CONSTRAINT ck_ast_ct_type;

ALTER TABLE af_novadesk.ast_custody_transfers
    ADD CONSTRAINT ck_ast_ct_type CHECK (transfer_type IN ('ASSIGNMENT','RETURN','REASSIGNMENT','WRITE_OFF','DISPOSAL','LOST'));
