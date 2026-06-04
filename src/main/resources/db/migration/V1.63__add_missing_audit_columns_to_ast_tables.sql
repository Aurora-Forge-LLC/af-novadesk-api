-- =============================================================================
-- NOVADESK API - Add missing AbstractEntity columns to asset tables
-- Version  : 1.63
-- Created  : 2026-06-03
-- Purpose  : ast_asset_returns, ast_custody_transfers, and
--            ast_depreciation_schedules were created without the status
--            and updated_at columns that AbstractEntity requires.
--            This migration backfills them.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_asset_returns
    ADD COLUMN IF NOT EXISTS status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW();

ALTER TABLE af_novadesk.ast_custody_transfers
    ADD COLUMN IF NOT EXISTS status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW();

ALTER TABLE af_novadesk.ast_depreciation_schedules
    ADD COLUMN IF NOT EXISTS status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW();
