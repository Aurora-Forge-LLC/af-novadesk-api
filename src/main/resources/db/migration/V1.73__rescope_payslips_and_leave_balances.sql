-- =============================================================================
-- NOVADESK API - Re-scope payslips and leave balances
-- Version  : 1.73
-- Created  : 2026-06-05
-- Purpose  :
--   pr_payslips: Replace legal_entity_id FK with loose entity_assignment_id
--     (cm_employee_entity_assignments) + denormalized organization_id.
--     PayrollBatch still carries legalEntityId for batch-level scoping.
--     Payslips link to the specific employee's entity assignment instead.
--
--   pr_leave_balances: Replace legal_entity_id FK with organization_id.
--     Leave is org-scoped — an employee's leave follows them across entities.
-- =============================================================================

SET search_path TO af_novadesk;

-- ═══════════════════════════════════════════════════════════════════════════════
-- pr_payslips
-- ═══════════════════════════════════════════════════════════════════════════════

-- 1. Add new columns
ALTER TABLE af_novadesk.pr_payslips
    ADD COLUMN IF NOT EXISTS entity_assignment_id UUID,
    ADD COLUMN IF NOT EXISTS organization_id      UUID;

-- 2. Populate entity_assignment_id from primary entity assignment of employee
UPDATE af_novadesk.pr_payslips ps
SET entity_assignment_id = ea.id
FROM af_novadesk.pr_employees pe
JOIN af_novadesk.cm_employees cm
    ON cm.auth_user_id = pe.auth_user_id AND cm.organization_id = pe.organization_id
JOIN af_novadesk.cm_employee_entity_assignments ea
    ON ea.employee_id = cm.id AND ea.is_primary_entity = TRUE
WHERE ps.employee_id = pe.id;

-- 3. Populate organization_id from the payroll batch → legal_entity → organization_id
UPDATE af_novadesk.pr_payslips ps
SET organization_id = le.organization_id
FROM af_novadesk.pr_payroll_batches pb
JOIN af_novadesk.legal_entities le ON le.id = pb.legal_entity_id
WHERE ps.payroll_batch_id = pb.id
  AND ps.organization_id IS NULL;

-- 4. Set NOT NULL (data should now be populated)
ALTER TABLE af_novadesk.pr_payslips
    ALTER COLUMN organization_id SET NOT NULL;

-- entity_assignment_id may still be NULL for legacy payslips with no cm_employees record
-- Make it nullable for safety; new payslips will always have it set

-- 5. Drop old index and FK
DROP INDEX IF EXISTS af_novadesk.idx_ps_entity_period;

ALTER TABLE af_novadesk.pr_payslips
    DROP CONSTRAINT IF EXISTS fk_ps_legal_entity;

-- 6. Drop legal_entity_id column
ALTER TABLE af_novadesk.pr_payslips
    DROP COLUMN IF EXISTS legal_entity_id;

-- 7. Add new indexes
CREATE INDEX IF NOT EXISTS idx_ps_org_period
    ON af_novadesk.pr_payslips (organization_id, pay_period_start);

CREATE INDEX IF NOT EXISTS idx_ps_entity_assignment
    ON af_novadesk.pr_payslips (entity_assignment_id)
    WHERE entity_assignment_id IS NOT NULL;


-- ═══════════════════════════════════════════════════════════════════════════════
-- pr_leave_balances
-- ═══════════════════════════════════════════════════════════════════════════════

-- 1. Add organization_id column
ALTER TABLE af_novadesk.pr_leave_balances
    ADD COLUMN IF NOT EXISTS organization_id UUID;

-- 2. Populate organization_id from legal_entity → organization_id
UPDATE af_novadesk.pr_leave_balances lb
SET organization_id = le.organization_id
FROM af_novadesk.legal_entities le
WHERE lb.legal_entity_id = le.id;

-- 3. Set NOT NULL
ALTER TABLE af_novadesk.pr_leave_balances
    ALTER COLUMN organization_id SET NOT NULL;

-- 4. Drop old index and FK
DROP INDEX IF EXISTS af_novadesk.idx_lb_entity_id;

ALTER TABLE af_novadesk.pr_leave_balances
    DROP CONSTRAINT IF EXISTS fk_lb_legal_entity;

-- 5. Drop legal_entity_id column
ALTER TABLE af_novadesk.pr_leave_balances
    DROP COLUMN IF EXISTS legal_entity_id;

-- 6. Add new index on organization_id
CREATE INDEX IF NOT EXISTS idx_lb_org_id
    ON af_novadesk.pr_leave_balances (organization_id);

COMMENT ON COLUMN af_novadesk.pr_payslips.entity_assignment_id IS
    'Loose ref to cm_employee_entity_assignments.id — the primary entity assignment that processed this payslip.';

COMMENT ON COLUMN af_novadesk.pr_leave_balances.organization_id IS
    'Org-scoped — leave follows the employee across entities within the same org.';
