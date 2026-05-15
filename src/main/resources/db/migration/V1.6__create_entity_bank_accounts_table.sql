-- =============================================================================
-- NOVADESK API - Create entity_bank_accounts Table
-- Version  : 1.6
-- Created  : 2026-05-15
-- Purpose  : Create the entity_bank_accounts table for storing bank and cash
--            account records belonging to a legal entity.
--
-- Table    : af_novadesk.entity_bank_accounts
-- =============================================================================

-- Create entity_bank_accounts table
CREATE TABLE af_novadesk.entity_bank_accounts (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id UUID NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    account_label VARCHAR(150) NOT NULL,
    bank_name VARCHAR(150),
    account_number VARCHAR(50),
    iban VARCHAR(34),
    swift_code VARCHAR(11),
    is_system_generated BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_bank_account_entity_type UNIQUE (legal_entity_id, account_type),
    CONSTRAINT fk_bank_account_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_bank_account_legal_entity ON af_novadesk.entity_bank_accounts (legal_entity_id);
CREATE INDEX idx_bank_account_account_type ON af_novadesk.entity_bank_accounts (legal_entity_id, account_type);

-- Add comments
COMMENT ON TABLE af_novadesk.entity_bank_accounts IS 'Bank and cash account records belonging to a legal entity';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.legal_entity_id IS 'Reference to the legal entity that owns this bank account';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.account_type IS 'Functional type: CASH, OPERATING, PAYROLL, etc.';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.account_label IS 'Display name for this account record (e.g. Main Operating Account – US)';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.bank_name IS 'Name of the bank institution';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.account_number IS 'Bank account number (stored for reference only)';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.iban IS 'IBAN for international wire transfers';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.swift_code IS 'SWIFT / BIC code of the bank';
COMMENT ON COLUMN af_novadesk.entity_bank_accounts.is_system_generated IS 'True if auto-generated from template on entity approval';

