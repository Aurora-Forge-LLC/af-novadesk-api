-- =============================================================================
-- NOVADESK API - Add balance column to organization_bank_info
-- Version  : 1.32
-- Created  : 2026-05-26
-- Purpose  : Add a balance (current available funds) column to the
--            organization_bank_info table so that Super_admin users can
--            track the available balance for capital injections and
--            inter-entity money transfers.
--
-- Table    : af_novadesk.organization_bank_info
-- =============================================================================

-- Add balance column with default 0
ALTER TABLE af_novadesk.organization_bank_info
    ADD COLUMN IF NOT EXISTS balance DECIMAL(19,4) NOT NULL DEFAULT 0.0000;

-- Add comment
COMMENT ON COLUMN af_novadesk.organization_bank_info.balance
    IS 'Current available balance in the account, used as the source of funds for capital injections and entity transfers';
