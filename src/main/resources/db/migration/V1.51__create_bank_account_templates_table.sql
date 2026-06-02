-- =============================================================================
-- NOVADESK API - Create bank_account_templates Table
-- Version  : 1.51
-- Created  : 2026-06-02
-- Purpose  : Country-specific default bank account templates used during
--            entity approval seeding.
--
-- Table    : af_novadesk.bank_account_templates
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.bank_account_templates (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code    VARCHAR(5)    NOT NULL,
    account_type    VARCHAR(20)   NOT NULL,
    account_label   VARCHAR(150)  NOT NULL,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bank_tmpl_country_type UNIQUE (country_code, account_type)
);

CREATE INDEX IF NOT EXISTS idx_bank_tmpl_country
    ON af_novadesk.bank_account_templates (country_code);

COMMENT ON TABLE af_novadesk.bank_account_templates IS
    'Country-specific default bank account templates used during entity approval';
COMMENT ON COLUMN af_novadesk.bank_account_templates.country_code IS 'ISO alpha-2 country code';
COMMENT ON COLUMN af_novadesk.bank_account_templates.account_type IS 'CASH, OPERATING, SAVINGS, PAYROLL, etc.';
COMMENT ON COLUMN af_novadesk.bank_account_templates.account_label IS 'Display label for the account';
COMMENT ON COLUMN af_novadesk.bank_account_templates.sort_order IS 'Display order within the country template';
