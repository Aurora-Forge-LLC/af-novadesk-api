-- =============================================================================
-- NOVADESK API - Payroll Details table + backfill common employee tables
-- Version  : 1.72
-- Created  : 2026-06-05
-- Purpose  :
--   1. Create pr_payroll_details — salary and bank data split out of
--      pr_employees into a dedicated payroll record. One row per employee;
--      linked to the primary entity assignment in cm_employee_entity_assignments.
--
--   2. Backfill cm_employees from existing pr_employees data so that every
--      payroll employee also has a canonical common identity record.
--
--   3. Backfill cm_employee_entity_assignments from pr_employees.
--      The first (earliest hired) assignment per employee is marked
--      is_primary_entity = TRUE.
--
--   4. Backfill pr_payroll_details from pr_employees salary/bank fields,
--      linked to the primary entity assignment created in step 3.
--
--   pr_employees is left untouched — removal of its identity/compensation
--   columns is deferred to Phase 5 (after all consumers are migrated).
-- =============================================================================

SET search_path TO af_novadesk;

-- ── 1. pr_payroll_details ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payroll_details (
    id                   UUID          PRIMARY KEY DEFAULT public.gen_random_uuid(),
    employee_id          UUID          NOT NULL,   -- loose ref → cm_employees.id
    entity_assignment_id UUID          NOT NULL,   -- loose ref → cm_employee_entity_assignments.id (primary entity)

    base_salary          DECIMAL(19,4) NOT NULL,
    salary_currency      CHAR(3)       NOT NULL,
    pay_frequency        VARCHAR(20)   NOT NULL DEFAULT 'MONTHLY',
    bank_account_number  VARCHAR(50),
    bank_name            VARCHAR(150),
    bank_ifsc_code       VARCHAR(20),

    status               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    record_status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_pr_payroll_employee  UNIQUE (employee_id),          -- one payroll per employee
    CONSTRAINT ck_pr_payroll_frequency CHECK  (pay_frequency IN ('MONTHLY','BIWEEKLY','WEEKLY')),
    CONSTRAINT ck_pr_payroll_status    CHECK  (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_pr_payroll_entity_assignment
    ON af_novadesk.pr_payroll_details (entity_assignment_id);

COMMENT ON TABLE af_novadesk.pr_payroll_details IS
    'Salary and bank details for a payroll employee. One row per employee. '
    'Linked to the primary entity assignment that processes their payroll.';

COMMENT ON COLUMN af_novadesk.pr_payroll_details.employee_id IS
    'Loose reference to cm_employees.id — not a hard FK to keep payroll module decoupled.';

COMMENT ON COLUMN af_novadesk.pr_payroll_details.entity_assignment_id IS
    'Loose reference to cm_employee_entity_assignments.id (primary entity only).';

-- ── 2. Backfill cm_employees from pr_employees ────────────────────────────────
-- Insert one cm_employees row per distinct (organization_id, auth_user_id).
-- If an employee works across multiple entities (multiple pr_employees rows),
-- they get a single cm_employees row using data from their earliest hire record.

INSERT INTO af_novadesk.cm_employees (
    id, organization_id, auth_user_id,
    employee_code, display_name, email,
    status, record_status,
    created_at, updated_at
)
SELECT DISTINCT ON (pe.organization_id, pe.auth_user_id)
    public.gen_random_uuid(),
    pe.organization_id,
    pe.auth_user_id,
    pe.employee_code,
    pe.first_name || ' ' || pe.last_name,
    pe.email,
    CASE WHEN pe.status = 'ACTIVE' THEN 'ACTIVE'
         WHEN pe.status = 'INACTIVE' THEN 'INACTIVE'
         ELSE 'ACTIVE' END,
    'ACTIVE',
    pe.created_at,
    pe.updated_at
FROM af_novadesk.pr_employees pe
ORDER BY pe.organization_id, pe.auth_user_id, pe.hire_date ASC
ON CONFLICT (organization_id, auth_user_id) DO NOTHING;

-- ── 3. Backfill cm_employee_entity_assignments from pr_employees ──────────────
-- One row per (employee, entity) pair.
-- The earliest hire date per employee is marked as primary.

INSERT INTO af_novadesk.cm_employee_entity_assignments (
    id, employee_id, legal_entity_id, organization_id,
    department, designation,
    is_primary_entity, hire_date, termination_date,
    status, record_status,
    created_at, updated_at
)
SELECT
    public.gen_random_uuid(),
    cm.id,
    pe.legal_entity_id,
    pe.organization_id,
    pe.department,
    pe.designation,
    -- Mark as primary if this is the earliest hire date for this employee
    (pe.hire_date = first_hire.min_hire_date),
    pe.hire_date,
    pe.termination_date,
    CASE WHEN pe.status = 'ACTIVE' THEN 'ACTIVE'
         WHEN pe.status = 'INACTIVE' THEN 'INACTIVE'
         ELSE 'ACTIVE' END,
    'ACTIVE',
    pe.created_at,
    pe.updated_at
FROM af_novadesk.pr_employees pe
JOIN af_novadesk.cm_employees cm
    ON cm.auth_user_id = pe.auth_user_id
   AND cm.organization_id = pe.organization_id
JOIN (
    SELECT auth_user_id, organization_id, MIN(hire_date) AS min_hire_date
    FROM af_novadesk.pr_employees
    GROUP BY auth_user_id, organization_id
) first_hire
    ON first_hire.auth_user_id = pe.auth_user_id
   AND first_hire.organization_id = pe.organization_id
ON CONFLICT (employee_id, legal_entity_id) DO NOTHING;

-- ── 4. Backfill pr_payroll_details from pr_employees ─────────────────────────
-- One row per employee using salary/bank from the primary entity assignment.

INSERT INTO af_novadesk.pr_payroll_details (
    id, employee_id, entity_assignment_id,
    base_salary, salary_currency,
    bank_account_number, bank_name, bank_ifsc_code,
    status, record_status,
    created_at, updated_at
)
SELECT
    public.gen_random_uuid(),
    cm.id,
    ea.id,
    pe.base_salary,
    pe.salary_currency,
    pe.bank_account_number,
    pe.bank_name,
    pe.bank_ifsc_code,
    'ACTIVE',
    'ACTIVE',
    pe.created_at,
    pe.updated_at
FROM af_novadesk.pr_employees pe
JOIN af_novadesk.cm_employees cm
    ON cm.auth_user_id = pe.auth_user_id
   AND cm.organization_id = pe.organization_id
JOIN af_novadesk.cm_employee_entity_assignments ea
    ON ea.employee_id = cm.id
   AND ea.legal_entity_id = pe.legal_entity_id
   AND ea.is_primary_entity = TRUE
ON CONFLICT (employee_id) DO NOTHING;
