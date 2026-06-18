-- =============================================================================
-- NOVADESK API - Add tax_name to pr_tax_configurations
-- Version  : 1.91
-- Purpose  : Add a human-readable label (e.g. "Income Tax 2026") for UI display.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_tax_configurations
    ADD COLUMN tax_name VARCHAR(100);

COMMENT ON COLUMN af_novadesk.pr_tax_configurations.tax_name IS
    'Human-readable label for the tax configuration, e.g. "Income Tax 2026" (PAY-04.6).';
