-- =============================================================================
-- NOVADESK API - Create fa_account_templates Table
-- Version  : 1.52
-- Created  : 2026-06-02
-- Purpose  : Country-specific funding account templates (fa_accounts) used
--            during entity approval to seed capital-injection accounts.
--
-- Table    : af_novadesk.fa_account_templates
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fa_account_templates (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code    VARCHAR(5)    NOT NULL,
    account_code    VARCHAR(30)   NOT NULL,
    account_name    VARCHAR(150)  NOT NULL,
    account_role    VARCHAR(50)   NOT NULL,
    account_type    VARCHAR(30)   NOT NULL,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fa_tmpl_country_code UNIQUE (country_code, account_code)
);

CREATE INDEX IF NOT EXISTS idx_fa_tmpl_country
    ON af_novadesk.fa_account_templates (country_code);

COMMENT ON TABLE af_novadesk.fa_account_templates IS
    'Country-specific funding account templates for capital-injection workflow seeding';
COMMENT ON COLUMN af_novadesk.fa_account_templates.country_code IS 'ISO alpha-2 country code';
COMMENT ON COLUMN af_novadesk.fa_account_templates.account_code IS 'Account code unique within the country template';
COMMENT ON COLUMN af_novadesk.fa_account_templates.account_name IS 'Human-readable account name';
COMMENT ON COLUMN af_novadesk.fa_account_templates.account_role IS 'Functional role: BANK_OPERATING, CASH, FOUNDER_EQUITY, etc.';
COMMENT ON COLUMN af_novadesk.fa_account_templates.account_type IS 'ASSET, LIABILITY, EQUITY, or REVENUE';
COMMENT ON COLUMN af_novadesk.fa_account_templates.sort_order IS 'Display/processing order within the country template';
