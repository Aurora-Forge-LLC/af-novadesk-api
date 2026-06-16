-- =============================================================================
-- NOVADESK API - Add calculation_method to pr_tax_configurations
-- Version  : 1.96
-- Purpose  : Supports two calculation models: PROGRESSIVE (slab-based) and
--            FLAT_ON_CAP (flat rate on capped monthly base — SSF/PF).
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_tax_configurations
    ADD COLUMN IF NOT EXISTS calculation_method VARCHAR(30) NOT NULL DEFAULT 'PROGRESSIVE',
    ADD CONSTRAINT chk_tc_calculation_method
        CHECK (calculation_method IN ('PROGRESSIVE', 'FLAT_ON_CAP'));

COMMENT ON COLUMN af_novadesk.pr_tax_configurations.calculation_method IS
    'Calculation method: PROGRESSIVE (slab-based on annual income) or FLAT_ON_CAP (flat rate on capped monthly base)';
