-- =============================================================================
-- NOVADESK API - Create coa_templates Table
-- Version  : 1.50
-- Created  : 2026-06-02
-- Purpose  : Create the coa_templates table for country-specific Chart of
--            Accounts default data. Populated via migration SQL, queried at
--            runtime during entity approval to seed per-entity CoA entries.
--
-- Table    : af_novadesk.coa_templates
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.coa_templates (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code        VARCHAR(5)    NOT NULL,
    account_code        VARCHAR(20)   NOT NULL,
    account_name        VARCHAR(150)  NOT NULL,
    account_type        VARCHAR(20)   NOT NULL,
    description         VARCHAR(500),
    parent_account_code VARCHAR(20),
    is_postable         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order          INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coa_template_country_code UNIQUE (country_code, account_code)
);

CREATE INDEX IF NOT EXISTS idx_coa_templates_country
    ON af_novadesk.coa_templates (country_code);

COMMENT ON TABLE af_novadesk.coa_templates IS
    'Country-specific Chart of Accounts templates used during entity approval seeding';
COMMENT ON COLUMN af_novadesk.coa_templates.country_code IS 'ISO alpha-2 country code (US, IN, NP, etc.)';
COMMENT ON COLUMN af_novadesk.coa_templates.account_code IS 'Numeric or alphanumeric account code';
COMMENT ON COLUMN af_novadesk.coa_templates.account_name IS 'Human-readable account name';
COMMENT ON COLUMN af_novadesk.coa_templates.account_type IS 'ASSET, LIABILITY, EQUITY, REVENUE, or EXPENSE';
COMMENT ON COLUMN af_novadesk.coa_templates.parent_account_code IS 'Optional self-referential parent account code for hierarchy';
COMMENT ON COLUMN af_novadesk.coa_templates.is_postable IS 'True if journal entries can be posted directly';
COMMENT ON COLUMN af_novadesk.coa_templates.sort_order IS 'Display/processing order within the country template';
