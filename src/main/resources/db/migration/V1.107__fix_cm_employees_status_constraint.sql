-- =============================================================================
-- NOVADESK API - Fix cm_employees status check constraint
-- Version  : 1.107
-- Created  : 2026-06-15
-- Purpose  : The original ck_cm_emp_status constraint allowed only
--            ('ACTIVE','INACTIVE','OFFBOARDING'), but the Java EmployeeStatus
--            enum uses PENDING_SETUP and OFFBOARDED. OFFBOARDING was never a
--            valid status in code. This migration drops the old constraint and
--            recreates it with the correct values matching the enum:
--            PENDING_SETUP, ACTIVE, INACTIVE, OFFBOARDED.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.cm_employees
    DROP CONSTRAINT IF EXISTS ck_cm_emp_status;

ALTER TABLE af_novadesk.cm_employees
    ADD CONSTRAINT ck_cm_emp_status
        CHECK (status IN ('PENDING_SETUP', 'ACTIVE', 'INACTIVE', 'OFFBOARDED'));
