-- =============================================================================
-- NOVADESK API - Drop employee_code from pr_employees
-- Version  : 1.81
-- Created  : 2026-06-09
-- Purpose  :
--   V1.74 slimmed pr_employees as part of the Phase 5 common-module
--   refactoring, moving identity data to cm_employees. However, it missed
--   dropping the employee_code column. The Employee Java entity no longer
--   maps this column, so Hibernate's INSERT omits it — causing a NOT NULL
--   constraint violation on every new employee onboard.
--
--   employee_code now lives in cm_employees (organisation_id, employee_code)
--   with its own unique constraint (uk_cm_emp_code). It is no longer needed
--   in pr_employees.
-- =============================================================================

SET search_path TO af_novadesk, public;

ALTER TABLE af_novadesk.pr_employees
    DROP COLUMN IF EXISTS employee_code;
