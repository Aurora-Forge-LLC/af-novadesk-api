-- =============================================================================
-- NOVADESK API - Slim pr_employees — drop identity, entity, and compensation columns
-- Version  : 1.74
-- Created  : 2026-06-05
-- Purpose  :
--   Phase 5 of the employee refactoring. Now that identity data lives in
--   cm_employees, entity assignments in cm_employee_entity_assignments, and
--   compensation in pr_payroll_details, the corresponding columns in
--   pr_employees are redundant and are removed here.
--
--   What remains in pr_employees after this migration:
--     id, organization_id, cm_employee_id (→ cm_employees),
--     manager_id (self-referential, payroll hierarchy),
--     status, record_status, created_at, updated_at
-- =============================================================================

SET search_path TO af_novadesk;

-- ── 1. Add cm_employee_id — the link from payroll record to canonical identity ─

ALTER TABLE af_novadesk.pr_employees
    ADD COLUMN IF NOT EXISTS cm_employee_id UUID;

-- Populate from cm_employees by matching auth_user_id
UPDATE af_novadesk.pr_employees pe
SET cm_employee_id = cm.id
FROM af_novadesk.cm_employees cm
WHERE cm.auth_user_id = pe.auth_user_id
  AND cm.organization_id = pe.organization_id;

CREATE INDEX IF NOT EXISTS idx_emp_cm_employee_id
    ON af_novadesk.pr_employees (cm_employee_id)
    WHERE cm_employee_id IS NOT NULL;

-- ── 2. Drop identity columns ──────────────────────────────────────────────────

ALTER TABLE af_novadesk.pr_employees
    DROP CONSTRAINT IF EXISTS fk_emp_shadow_user,
    DROP COLUMN IF EXISTS shadow_user_id,
    DROP COLUMN IF EXISTS auth_user_id,
    DROP COLUMN IF EXISTS first_name,
    DROP COLUMN IF EXISTS last_name,
    DROP COLUMN IF EXISTS email;

-- ── 3. Drop entity assignment columns ────────────────────────────────────────

ALTER TABLE af_novadesk.pr_employees
    DROP CONSTRAINT IF EXISTS fk_emp_legal_entity,
    DROP COLUMN IF EXISTS legal_entity_id,
    DROP COLUMN IF EXISTS department,
    DROP COLUMN IF EXISTS designation,
    DROP COLUMN IF EXISTS hire_date,
    DROP COLUMN IF EXISTS termination_date;

-- ── 4. Drop compensation columns ─────────────────────────────────────────────
-- Salary and bank data now live in pr_payroll_details.

ALTER TABLE af_novadesk.pr_employees
    DROP COLUMN IF EXISTS base_salary,
    DROP COLUMN IF EXISTS salary_currency,
    DROP COLUMN IF EXISTS bank_account_number,
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS bank_ifsc_code;

-- ── 5. Drop stale indexes ─────────────────────────────────────────────────────

DROP INDEX IF EXISTS af_novadesk.idx_emp_entity_status;
DROP INDEX IF EXISTS af_novadesk.idx_emp_shadow_user;

-- Unique constraints that included dropped columns
ALTER TABLE af_novadesk.pr_employees
    DROP CONSTRAINT IF EXISTS uk_emp_user_entity,
    DROP CONSTRAINT IF EXISTS uk_emp_code_entity;

COMMENT ON TABLE af_novadesk.pr_employees IS
    'Thin payroll employee record after Phase 5 refactoring. '
    'Identity → cm_employees (cm_employee_id). '
    'Entity assignments → cm_employee_entity_assignments. '
    'Compensation → pr_payroll_details. '
    'manager_id remains for payroll approval hierarchy.';
