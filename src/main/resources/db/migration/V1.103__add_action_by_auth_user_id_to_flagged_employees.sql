-- V1.95: Add action_by_auth_user_id column to pr_payroll_flagged_employees
-- Supports SUPER_ADMIN users who take action on flagged employees without being an employee themselves.

ALTER TABLE af_novadesk.pr_payroll_flagged_employees
    ADD COLUMN IF NOT EXISTS action_by_auth_user_id UUID;
