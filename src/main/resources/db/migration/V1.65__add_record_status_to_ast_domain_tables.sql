-- =============================================================================
-- NOVADESK API - Add record_status column to asset domain tables
-- Version  : 1.65
-- Created  : 2026-06-03
-- Purpose  : ast_assets, ast_asset_assignments, and ast_write_offs each have
--            a domain-specific 'status' column (AssetStatus, AssignmentStatus,
--            WriteOffStatus). AbstractEntity also maps to a column named 'status'.
--            The conflict is resolved by giving AbstractEntity its own column
--            'record_status' on these tables, while domain status stays in 'status'.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_assets
    ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE af_novadesk.ast_asset_assignments
    ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE af_novadesk.ast_write_offs
    ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
