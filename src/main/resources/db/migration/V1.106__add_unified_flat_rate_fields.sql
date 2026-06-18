-- =============================================================================
-- NOVADESK API - Add unified flat-rate fields to pr_tax_configurations
-- Version  : 1.98
-- Purpose  : Replace the confusing ssf_*/pf_* dual-field system with a single
--            set of unified fields for ALL FLAT_ON_CAP tax configurations.
--            The tax_type field becomes purely cosmetic (label only).
-- =============================================================================

SET search_path TO af_novadesk;

-- 1. Add unified columns
ALTER TABLE af_novadesk.pr_tax_configurations
    ADD COLUMN IF NOT EXISTS flat_employee_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flat_employer_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flat_cap_amount     DECIMAL(19,4);

COMMENT ON COLUMN af_novadesk.pr_tax_configurations.flat_employee_rate IS
    'Employee-side flat rate as percentage (e.g. 6.2 = 6.2%). Code divides by 100.';
COMMENT ON COLUMN af_novadesk.pr_tax_configurations.flat_employer_rate IS
    'Employer-side flat rate as percentage (0 = no employer contribution).';
COMMENT ON COLUMN af_novadesk.pr_tax_configurations.flat_cap_amount IS
    'Monthly wage cap for flat-rate calculation (NULL = no cap / unlimited).';

-- 2. Backfill from ssf_* fields (Social Security / Nepal SSF)
UPDATE af_novadesk.pr_tax_configurations
SET flat_employee_rate = COALESCE(ssf_employee_rate, 0),
    flat_employer_rate = COALESCE(ssf_employer_rate, 0),
    flat_cap_amount     = ssf_max_cap_amount
WHERE calculation_method = 'FLAT_ON_CAP'
  AND flat_employee_rate = 0
  AND (ssf_employee_rate IS NOT NULL AND ssf_employee_rate > 0);

-- 3. Backfill from pf_* fields (Provident Fund / Medicare)
UPDATE af_novadesk.pr_tax_configurations
SET flat_employee_rate = COALESCE(pf_employee_rate, 0),
    flat_employer_rate = COALESCE(pf_employer_rate, 0),
    flat_cap_amount     = pf_max_cap_amount
WHERE calculation_method = 'FLAT_ON_CAP'
  AND flat_employee_rate = 0
  AND (pf_employee_rate IS NOT NULL AND pf_employee_rate > 0);
