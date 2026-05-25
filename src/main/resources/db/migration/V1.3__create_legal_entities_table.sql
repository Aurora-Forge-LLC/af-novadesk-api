-- =============================================================================
-- NOVADESK API - Create legal_entities Table
-- Version  : 1.3
-- Created  : 2026-05-15
-- Purpose  : Create the legal_entities table for storing independent legal
--            entities (e.g. country-specific subsidiaries) whose financial
--            records are maintained in isolation from other entities.
--
-- Table    : af_novadesk.legal_entities
-- =============================================================================

-- Create legal_entities table
CREATE TABLE IF NOT EXISTS af_novadesk.legal_entities (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    entity_name VARCHAR(100) NOT NULL,
    entity_code VARCHAR(10) NOT NULL,
    country VARCHAR(5) NOT NULL,
    base_currency VARCHAR(5) NOT NULL,
    tax_id VARCHAR(50),
    incorporation_date DATE NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_legal_entity_name UNIQUE (entity_name),
    CONSTRAINT uk_legal_entity_code UNIQUE (entity_code)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_legal_entity_country ON af_novadesk.legal_entities (country);
CREATE INDEX IF NOT EXISTS idx_legal_entity_approval_status ON af_novadesk.legal_entities (approval_status);

-- Add comments
COMMENT ON TABLE af_novadesk.legal_entities IS 'Independent legal entities (e.g. country-specific subsidiaries) with isolated financial records';
COMMENT ON COLUMN af_novadesk.legal_entities.entity_name IS 'Human-readable name; must be unique across all entities';
COMMENT ON COLUMN af_novadesk.legal_entities.entity_code IS 'Short alphanumeric code (2-10 chars) used as prefix in document numbers';
COMMENT ON COLUMN af_novadesk.legal_entities.country IS 'ISO alpha-2 country code; drives base currency and fiscal-year template';
COMMENT ON COLUMN af_novadesk.legal_entities.base_currency IS 'ISO 4217 currency code derived from country';
COMMENT ON COLUMN af_novadesk.legal_entities.tax_id IS 'Government-issued tax or company registration number';
COMMENT ON COLUMN af_novadesk.legal_entities.incorporation_date IS 'Legal date of incorporation';
COMMENT ON COLUMN af_novadesk.legal_entities.approval_status IS 'Finance-team approval state: PENDING, APPROVED, REJECTED';

