-- =============================================================================
-- NOVADESK API - Create chart_of_accounts Table
-- Version  : 1.5
-- Created  : 2026-05-15
-- Purpose  : Create the chart_of_accounts table for storing account entries
--            within a legal entity's Chart of Accounts.
--
-- Table    : af_novadesk.chart_of_accounts
-- =============================================================================

-- Create chart_of_accounts table
CREATE TABLE af_novadesk.chart_of_accounts (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id UUID NOT NULL,
    parent_account_id UUID,
    account_code VARCHAR(20) NOT NULL,
    account_name VARCHAR(150) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    is_postable BOOLEAN NOT NULL DEFAULT true,
    is_system_generated BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_coa_entity_account_code UNIQUE (legal_entity_id, account_code),
    CONSTRAINT fk_coa_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE,
    CONSTRAINT fk_coa_parent_account FOREIGN KEY (parent_account_id)
        REFERENCES af_novadesk.chart_of_accounts(id) ON DELETE SET NULL
);

-- Create indexes
CREATE INDEX idx_coa_legal_entity ON af_novadesk.chart_of_accounts (legal_entity_id);
CREATE INDEX idx_coa_parent_account ON af_novadesk.chart_of_accounts (parent_account_id);
CREATE INDEX idx_coa_account_type ON af_novadesk.chart_of_accounts (legal_entity_id, account_type);

-- Add comments
COMMENT ON TABLE af_novadesk.chart_of_accounts IS 'Account entries within a legal entity Chart of Accounts';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.legal_entity_id IS 'Reference to the legal entity that owns this account';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.parent_account_id IS 'Self-referential parent for hierarchical CoA structures';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.account_code IS 'Numeric or alphanumeric code following the entity CoA numbering scheme';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.account_name IS 'Human-readable account name (e.g. Cash and Cash Equivalents)';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.account_type IS 'Double-entry category: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.description IS 'Optional plain-text description of the account purpose';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.is_postable IS 'True if journal entries can be posted directly; false for header accounts';
COMMENT ON COLUMN af_novadesk.chart_of_accounts.is_system_generated IS 'True if auto-generated from country template';

