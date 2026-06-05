-- =============================================================================
-- NOVADESK API - Add manager_uuid to pr_employees
-- Version  : 1.69
-- Purpose  : Adds a nullable manager_uuid column to pr_employees.
--            Populated when the "Assign Manager Role" toggle is ON during
--            employee onboarding. NULL if the employee is not a manager.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.pr_employees
ADD COLUMN IF NOT EXISTS manager_uuid UUID NULL;

COMMENT ON COLUMN af_novadesk.pr_employees.manager_uuid
    IS 'Generated UUID when employee is assigned a MANAGER entity role. NULL for non-manager employees.';
