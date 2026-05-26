-- =============================================================================
-- NOVADESK API - Create organization_bank_info Table
-- Version  : 1.31
-- Created  : 2026-05-26
-- Purpose  : Create the organization_bank_info table for storing bank account
--            details at the organization level. Managed exclusively by
--            Super_admin users.
--
-- Table    : af_novadesk.organization_bank_info
-- =============================================================================

-- Create organization_bank_info table
CREATE TABLE IF NOT EXISTS af_novadesk.organization_bank_info (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),

    -- Organization & User references
    org_id UUID NOT NULL,
    user_id UUID NOT NULL,

    -- Bank details
    bank_name VARCHAR(150) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    iban VARCHAR(34),
    swift_code VARCHAR(11),
    bank_address VARCHAR(500),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    is_primary BOOLEAN NOT NULL DEFAULT false,

    -- Auditing & lifecycle
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- Constraints
    CONSTRAINT uk_org_bank_account_number UNIQUE (org_id, account_number)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_org_bank_info_org_id ON af_novadesk.organization_bank_info (org_id);
CREATE INDEX IF NOT EXISTS idx_org_bank_info_user_id ON af_novadesk.organization_bank_info (user_id);
CREATE INDEX IF NOT EXISTS idx_org_bank_info_status ON af_novadesk.organization_bank_info (status);

-- Add comments
COMMENT ON TABLE af_novadesk.organization_bank_info IS 'Organization-level bank account information managed by Super_admin users';
COMMENT ON COLUMN af_novadesk.organization_bank_info.org_id IS 'Reference to the organization this bank info belongs to';
COMMENT ON COLUMN af_novadesk.organization_bank_info.user_id IS 'UUID of the Super_admin user who manages this record';
COMMENT ON COLUMN af_novadesk.organization_bank_info.bank_name IS 'Name of the bank institution';
COMMENT ON COLUMN af_novadesk.organization_bank_info.account_holder_name IS 'Name of the account holder';
COMMENT ON COLUMN af_novadesk.organization_bank_info.account_number IS 'Bank account number';
COMMENT ON COLUMN af_novadesk.organization_bank_info.iban IS 'International Bank Account Number';
COMMENT ON COLUMN af_novadesk.organization_bank_info.swift_code IS 'SWIFT / BIC code of the bank';
COMMENT ON COLUMN af_novadesk.organization_bank_info.bank_address IS 'Physical address of the bank branch';
COMMENT ON COLUMN af_novadesk.organization_bank_info.currency IS 'ISO 4217 currency code (e.g. USD, NPR)';
COMMENT ON COLUMN af_novadesk.organization_bank_info.is_primary IS 'Whether this is the primary bank account for the organization';
