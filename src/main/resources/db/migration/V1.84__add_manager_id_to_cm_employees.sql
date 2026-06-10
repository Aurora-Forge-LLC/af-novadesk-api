-- V1.84: Add manager_id to cm_employees for payroll approval hierarchy
-- The manager relationship moves from pr_employees.manager_id to cm_employees.manager_id
-- as part of eliminating the pr_employees table.

-- 1. Add the column
ALTER TABLE af_novadesk.cm_employees
    ADD COLUMN manager_id UUID;

-- 2. Add foreign key (self-referential)
ALTER TABLE af_novadesk.cm_employees
    ADD CONSTRAINT fk_cm_emp_manager
        FOREIGN KEY (manager_id) REFERENCES af_novadesk.cm_employees(id);

-- 3. Index for manager lookups
CREATE INDEX idx_cm_emp_manager_id
    ON af_novadesk.cm_employees (manager_id);

-- 4. Backfill manager_id from the existing pr_employees table
--    pr_employees.cm_employee_id → pr_employees.manager_id → manager's cm_employee_id
UPDATE af_novadesk.cm_employees ce
    SET manager_id = sub.mgr_cm_employee_id
    FROM (
        SELECT
            pe.cm_employee_id              AS cm_employee_id,
            pe_mgr.cm_employee_id          AS mgr_cm_employee_id
        FROM af_novadesk.pr_employees pe
            LEFT JOIN af_novadesk.pr_employees pe_mgr
                      ON pe_mgr.id = pe.manager_id
        WHERE pe.cm_employee_id IS NOT NULL
          AND pe.manager_id IS NOT NULL
    ) sub
    WHERE ce.id = sub.cm_employee_id;

-- 5. Comment documenting the change
COMMENT ON COLUMN af_novadesk.cm_employees.manager_id IS
    'Self-referential FK for payroll approval hierarchy. Migrated from pr_employees.manager_id (V1.84).';
