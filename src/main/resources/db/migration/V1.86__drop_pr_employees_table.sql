-- V1.86: Drop the pr_employees table
-- All payroll entities now reference cm_employees.id directly.
-- Manager hierarchy has been migrated to cm_employees.manager_id in V1.84.
-- All FK columns have been re-pointed in V1.85.

-- The pr_employee_outbox_events.employee_id column already stored the pr_employees.id,
-- which was already the same value as cm_employees.id in most cases. Consumers should
-- now treat employee_id as referring to cm_employees.id.

DROP TABLE IF EXISTS af_novadesk.pr_employees CASCADE;
