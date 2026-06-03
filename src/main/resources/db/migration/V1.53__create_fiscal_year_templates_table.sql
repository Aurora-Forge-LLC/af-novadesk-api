-- =============================================================================
-- NOVADESK API - Create fiscal_year_templates Table
-- Version  : 1.53
-- Created  : 2026-06-02
-- Purpose  : Country-specific fiscal year templates used during entity approval.
--
-- Table    : af_novadesk.fiscal_year_templates
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fiscal_year_templates (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    country_code        VARCHAR(5)    NOT NULL UNIQUE,
    fiscal_start_month  INTEGER       NOT NULL,
    fiscal_start_day    INTEGER       NOT NULL,
    fiscal_end_month    INTEGER       NOT NULL,
    fiscal_end_day      INTEGER       NOT NULL,
    periods_per_year    INTEGER       NOT NULL DEFAULT 12,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fy_tmpl_country
    ON af_novadesk.fiscal_year_templates (country_code);

COMMENT ON TABLE af_novadesk.fiscal_year_templates IS
    'Country-specific fiscal year templates used during entity approval';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.country_code IS 'ISO alpha-2 country code (unique)';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.fiscal_start_month IS 'Month (1-12) the fiscal year starts';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.fiscal_start_day IS 'Day-of-month the fiscal year starts';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.fiscal_end_month IS 'Month (1-12) the fiscal year ends';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.fiscal_end_day IS 'Day-of-month the fiscal year ends';
COMMENT ON COLUMN af_novadesk.fiscal_year_templates.periods_per_year IS 'Number of accounting periods (e.g. 12 for monthly)';
