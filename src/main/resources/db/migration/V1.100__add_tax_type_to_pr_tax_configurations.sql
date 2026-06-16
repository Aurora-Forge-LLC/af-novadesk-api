-- =============================================================================
-- NOVADESK API - Add tax_type to pr_tax_configurations
-- Version  : 1.92
-- Created  : 2026-06-11
-- Purpose  : Allow multiple tax configurations per entity+jurisdiction by
--            adding a tax_type discriminator column.  The old unique constraint
--            (legal_entity_id, jurisdiction) is replaced with a three-column
--            constraint (legal_entity_id, jurisdiction, tax_type).
--            (LLR-PAY-04.6 enhancement)
-- =============================================================================

SET search_path TO af_novadesk;

-- 1. Add tax_type column with default for existing rows
ALTER TABLE af_novadesk.pr_tax_configurations
    ADD COLUMN IF NOT EXISTS tax_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL';

-- 2. Drop the old two-column unique constraint
ALTER TABLE af_novadesk.pr_tax_configurations
    DROP CONSTRAINT IF EXISTS uk_tc_entity_jurisdiction;

-- 3. Add the new three-column unique constraint
ALTER TABLE af_novadesk.pr_tax_configurations
    ADD CONSTRAINT uk_tc_entity_jurisdiction_type
        UNIQUE (legal_entity_id, jurisdiction, tax_type);

COMMENT ON COLUMN af_novadesk.pr_tax_configurations.tax_type IS
    'Discriminator for multiple tax configs within the same entity+jurisdiction.  Examples: GENERAL, FEDERAL, STATE, PF, INCOME_TAX, PROFESSIONAL_TAX, FICA, MEDICARE.  Nepal entities must have exactly one config (tax_type=GENERAL).';
