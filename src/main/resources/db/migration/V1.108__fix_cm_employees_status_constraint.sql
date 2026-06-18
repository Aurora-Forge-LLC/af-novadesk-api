-- =============================================================================
-- NOVADESK API - Fix cm_employees status CHECK constraint
-- Version  : 1.95
-- Created  : 2026-06-15
-- Purpose  : The Java EmployeeStatus enum defines PENDING_SETUP, ACTIVE,
--            INACTIVE, and OFFBOARDED, but the original V1.62 constraint
--            only allowed ACTIVE, INACTIVE, and OFFBOARDING (note: "ING"
--            vs "ED").  This mismatch caused offboarding to fail with:
--              ERROR: new row for relation "cm_employees" violates check
--              constraint "ck_cm_emp_status"
--            The constraint is dropped and recreated with all four valid
--            enum values matching EmployeeStatus.java.  Any existing rows
--            with the legacy OFFBOARDING value are migrated to OFFBOARDED
--            first to avoid constraint violations on future updates.
-- =============================================================================

SET search_path TO af_novadesk;

-- Normalise legacy OFFBOARDING → OFFBOARDED so the new constraint passes
-- on existing rows when they are later updated.
UPDATE af_novadesk.cm_employees
   SET status = 'OFFBOARDED'
 WHERE status = 'OFFBOARDING';

ALTER TABLE af_novadesk.cm_employees
    DROP CONSTRAINT IF EXISTS ck_cm_emp_status;

ALTER TABLE af_novadesk.cm_employees
    ADD CONSTRAINT ck_cm_emp_status
        CHECK (status IN ('PENDING_SETUP', 'ACTIVE', 'INACTIVE', 'OFFBOARDED'));

COMMENT ON COLUMN af_novadesk.cm_employees.status IS
    'PENDING_SETUP | ACTIVE | INACTIVE | OFFBOARDED';
