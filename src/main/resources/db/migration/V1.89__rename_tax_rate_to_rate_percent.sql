-- =============================================================================
-- NOVADESK API - Rename tax_rate to rate_percent in pr_tax_slabs
-- Version  : 1.89
-- Purpose  : Rename column to match frontend field name rate_percent.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_tax_slabs
    RENAME COLUMN tax_rate TO rate_percent;

COMMENT ON COLUMN af_novadesk.pr_tax_slabs.rate_percent IS
    'Tax rate as a percentage value, e.g. 3.00 for 3% (LLR-PAY-04.4).';
