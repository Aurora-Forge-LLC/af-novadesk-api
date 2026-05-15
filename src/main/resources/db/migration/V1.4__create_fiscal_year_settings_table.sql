-- =============================================================================
-- NOVADESK API - Create fiscal_year_settings Table
-- Version  : 1.4
-- Created  : 2026-05-15
-- Purpose  : Create the fiscal_year_settings table for storing country-specific
--            fiscal year configurations for legal entities.
--
-- Table    : af_novadesk.fiscal_year_settings
-- =============================================================================

-- Create fiscal_year_settings table
CREATE TABLE af_novadesk.fiscal_year_settings (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id UUID NOT NULL UNIQUE,
    fiscal_start_month INTEGER NOT NULL,
    fiscal_start_day INTEGER NOT NULL,
    fiscal_end_month INTEGER NOT NULL,
    fiscal_end_day INTEGER NOT NULL,
    current_fiscal_year INTEGER NOT NULL,
    periods_per_year INTEGER NOT NULL DEFAULT 12,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_fiscal_year_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE,
    CONSTRAINT check_fiscal_start_month CHECK (fiscal_start_month >= 1 AND fiscal_start_month <= 12),
    CONSTRAINT check_fiscal_start_day CHECK (fiscal_start_day >= 1 AND fiscal_start_day <= 31),
    CONSTRAINT check_fiscal_end_month CHECK (fiscal_end_month >= 1 AND fiscal_end_month <= 12),
    CONSTRAINT check_fiscal_end_day CHECK (fiscal_end_day >= 1 AND fiscal_end_day <= 31)
);

-- Create indexes
CREATE INDEX idx_fiscal_year_legal_entity ON af_novadesk.fiscal_year_settings (legal_entity_id);

-- Add comments
COMMENT ON TABLE af_novadesk.fiscal_year_settings IS 'Fiscal year configuration for legal entities, country-regulation-driven';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.legal_entity_id IS 'Reference to the legal entity (1:1 relationship)';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.fiscal_start_month IS 'Month (1-12) on which fiscal year starts';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.fiscal_start_day IS 'Day-of-month on which fiscal year starts';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.fiscal_end_month IS 'Month (1-12) on which fiscal year ends';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.fiscal_end_day IS 'Day-of-month on which fiscal year ends';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.current_fiscal_year IS 'Calendar year from which the current active fiscal year starts';
COMMENT ON COLUMN af_novadesk.fiscal_year_settings.periods_per_year IS 'Number of accounting periods per fiscal year (e.g. 12 for monthly)';

