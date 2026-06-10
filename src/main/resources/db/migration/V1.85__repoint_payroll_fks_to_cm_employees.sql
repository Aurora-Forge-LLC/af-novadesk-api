-- V1.85: Re-point all payroll employee_id FK columns from pr_employees.id → cm_employees.id
-- This is the core migration that eliminates the pr_employees intermediary table.
-- Each payroll table had employee_id referencing pr_employees.id.
-- We use a temp-column pattern: add temp → backfill → drop old FK → drop old col → rename → add new FK
--
-- NOTE: All existing FK constraints were created as inline REFERENCES (no explicit CONSTRAINT name),
-- so PostgreSQL auto-generated names follow the pattern: tablename_columnname_fkey.
-- We drop BOTH auto-generated and custom names defensively.

-- ═══════════════════════════════════════════════════════════════════════════════
-- 1. pr_leave_balances
-- ═══════════════════════════════════════════════════════════════════════════════

-- 1a. Add temp column
ALTER TABLE af_novadesk.pr_leave_balances
    ADD COLUMN cm_employee_id_temp UUID;

-- 1b. Backfill from pr_employees using the current employee_id value
UPDATE af_novadesk.pr_leave_balances lb
    SET cm_employee_id_temp = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lb.employee_id = pe.id;

-- 1c. Drop old FK constraint (auto-generated name + any custom name)
ALTER TABLE af_novadesk.pr_leave_balances
    DROP CONSTRAINT IF EXISTS pr_leave_balances_employee_id_fkey;
ALTER TABLE af_novadesk.pr_leave_balances
    DROP CONSTRAINT IF EXISTS fk_lb_employee;

-- 1d. Drop old column
ALTER TABLE af_novadesk.pr_leave_balances
    DROP COLUMN employee_id;

-- 1e. Rename temp column
ALTER TABLE af_novadesk.pr_leave_balances
    RENAME COLUMN cm_employee_id_temp TO employee_id;

-- 1f. Add NOT NULL and new FK to cm_employees
ALTER TABLE af_novadesk.pr_leave_balances
    ALTER COLUMN employee_id SET NOT NULL;
ALTER TABLE af_novadesk.pr_leave_balances
    ADD CONSTRAINT fk_lb_employee
        FOREIGN KEY (employee_id) REFERENCES af_novadesk.cm_employees(id);

-- 1g. Recreate index on the new column (using original name to match @Table index in LeaveBalance.java)
CREATE INDEX IF NOT EXISTS idx_lb_employee_id ON af_novadesk.pr_leave_balances (employee_id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 2. pr_leave_requests
-- ═══════════════════════════════════════════════════════════════════════════════

-- 2a. Drop old FK constraints (auto-generated names + any custom names)
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS pr_leave_requests_employee_id_fkey;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS pr_leave_requests_approver_id_fkey;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS pr_leave_requests_second_approver_id_fkey;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS pr_leave_requests_executive_approver_id_fkey;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS fk_lr_employee;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS fk_lr_approver;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS fk_lr_second_approver;
ALTER TABLE af_novadesk.pr_leave_requests
    DROP CONSTRAINT IF EXISTS fk_lr_executive_approver;

-- 2b. Backfill each FK column with the corresponding cm_employee_id
UPDATE af_novadesk.pr_leave_requests lr
    SET employee_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lr.employee_id = pe.id;

UPDATE af_novadesk.pr_leave_requests lr
    SET approver_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lr.approver_id = pe.id;

UPDATE af_novadesk.pr_leave_requests lr
    SET second_approver_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lr.second_approver_id = pe.id;

UPDATE af_novadesk.pr_leave_requests lr
    SET executive_approver_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lr.executive_approver_id = pe.id;

-- 2c. Add new FK constraints to cm_employees
ALTER TABLE af_novadesk.pr_leave_requests
    ADD CONSTRAINT fk_lr_employee
        FOREIGN KEY (employee_id) REFERENCES af_novadesk.cm_employees(id);
ALTER TABLE af_novadesk.pr_leave_requests
    ADD CONSTRAINT fk_lr_approver
        FOREIGN KEY (approver_id) REFERENCES af_novadesk.cm_employees(id);
ALTER TABLE af_novadesk.pr_leave_requests
    ADD CONSTRAINT fk_lr_second_approver
        FOREIGN KEY (second_approver_id) REFERENCES af_novadesk.cm_employees(id);
ALTER TABLE af_novadesk.pr_leave_requests
    ADD CONSTRAINT fk_lr_executive_approver
        FOREIGN KEY (executive_approver_id) REFERENCES af_novadesk.cm_employees(id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 3. pr_leave_transactions
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE af_novadesk.pr_leave_transactions
    DROP CONSTRAINT IF EXISTS pr_leave_transactions_employee_id_fkey;
ALTER TABLE af_novadesk.pr_leave_transactions
    DROP CONSTRAINT IF EXISTS fk_lt_employee;

UPDATE af_novadesk.pr_leave_transactions lt
    SET employee_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE lt.employee_id = pe.id;

ALTER TABLE af_novadesk.pr_leave_transactions
    ADD CONSTRAINT fk_lt_employee
        FOREIGN KEY (employee_id) REFERENCES af_novadesk.cm_employees(id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 4. pr_payslips
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE af_novadesk.pr_payslips
    DROP CONSTRAINT IF EXISTS pr_payslips_employee_id_fkey;
ALTER TABLE af_novadesk.pr_payslips
    DROP CONSTRAINT IF EXISTS fk_ps_employee;

UPDATE af_novadesk.pr_payslips ps
    SET employee_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE ps.employee_id = pe.id;

ALTER TABLE af_novadesk.pr_payslips
    ADD CONSTRAINT fk_ps_employee
        FOREIGN KEY (employee_id) REFERENCES af_novadesk.cm_employees(id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 5. pr_payroll_flagged_employees
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    DROP CONSTRAINT IF EXISTS pr_payroll_flagged_employees_employee_id_fkey;
ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    DROP CONSTRAINT IF EXISTS pr_payroll_flagged_employees_action_by_fkey;
ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    DROP CONSTRAINT IF EXISTS fk_pfe_employee;
ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    DROP CONSTRAINT IF EXISTS fk_pfe_action_by;

UPDATE af_novadesk.pr_payroll_flagged_employees pfe
    SET employee_id = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE pfe.employee_id = pe.id;

UPDATE af_novadesk.pr_payroll_flagged_employees pfe
    SET action_by = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE pfe.action_by = pe.id;

ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    ADD CONSTRAINT fk_pfe_employee
        FOREIGN KEY (employee_id) REFERENCES af_novadesk.cm_employees(id);
ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    ADD CONSTRAINT fk_pfe_action_by
        FOREIGN KEY (action_by) REFERENCES af_novadesk.cm_employees(id);

-- ═══════════════════════════════════════════════════════════════════════════════
-- 6. pr_payroll_audit_logs
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE af_novadesk.pr_payroll_audit_logs
    DROP CONSTRAINT IF EXISTS pr_payroll_audit_logs_performed_by_fkey;
ALTER TABLE af_novadesk.pr_payroll_audit_logs
    DROP CONSTRAINT IF EXISTS fk_pal_performed_by;

UPDATE af_novadesk.pr_payroll_audit_logs pal
    SET performed_by = pe.cm_employee_id
    FROM af_novadesk.pr_employees pe
    WHERE pal.performed_by = pe.id;

ALTER TABLE af_novadesk.pr_payroll_audit_logs
    ADD CONSTRAINT fk_pal_performed_by
        FOREIGN KEY (performed_by) REFERENCES af_novadesk.cm_employees(id);
