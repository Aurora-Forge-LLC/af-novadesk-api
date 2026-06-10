-- V1.78: Add leave_policy_id FK and earned_days column to pr_leave_balances.
-- Links each leave balance to a configurable leave policy instead of the hardcoded LeaveType enum.
-- Adds earned_days for tracking monthly accrual on earned leave policies.

-- Add leave_policy_id column (nullable during transition)
ALTER TABLE af_novadesk.pr_leave_balances
    ADD COLUMN leave_policy_id UUID
        CONSTRAINT fk_lb_leave_policy REFERENCES af_novadesk.pr_leave_policies(id);

-- Add earned_days column for tracking accrued earned leave
ALTER TABLE af_novadesk.pr_leave_balances
    ADD COLUMN earned_days NUMERIC(5,1) NOT NULL DEFAULT 0;

-- Drop old unique constraint (employee, leave_type, fiscal_year)
ALTER TABLE af_novadesk.pr_leave_balances
    DROP CONSTRAINT IF EXISTS uk_lb_employee_type_fiscal;

-- Add new unique constraint: one balance per employee, per policy, per fiscal year
ALTER TABLE af_novadesk.pr_leave_balances
    ADD CONSTRAINT uk_lb_employee_policy_fiscal
        UNIQUE (employee_id, leave_policy_id, fiscal_year_setting_id);

-- Index for policy-based lookups
CREATE INDEX IF NOT EXISTS idx_lb_policy_id ON af_novadesk.pr_leave_balances(leave_policy_id);
