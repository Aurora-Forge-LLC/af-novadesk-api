-- =============================================================================
-- NOVADESK API - Widen rate_percent column in pr_tax_slabs
-- Version  : 1.97
-- Purpose  : Change DECIMAL(5,4) → DECIMAL(5,2) so percentage values like
--            10%, 20%, 30% can be stored without numeric overflow.
--            Previously the column allowed max 9.9999, but common tax rates
--            (e.g. 10%, 12%, 20%) exceed that limit.
-- Reference: LLR-PAY-04.4
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_tax_slabs
    ALTER COLUMN rate_percent TYPE DECIMAL(5,2);
