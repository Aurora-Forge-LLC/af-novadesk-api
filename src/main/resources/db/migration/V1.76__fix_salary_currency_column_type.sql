-- =============================================================================
-- NOVADESK API - Fix salary_currency column type in pr_payroll_details
-- Version  : 1.76
-- Purpose  : salary_currency was defined as CHAR(3) (bpchar) in V1.72 which
--            conflicts with Hibernate's VARCHAR mapping for Java String.
--            Converting to VARCHAR(3) to match the entity mapping.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_payroll_details
    ALTER COLUMN salary_currency TYPE VARCHAR(3);
