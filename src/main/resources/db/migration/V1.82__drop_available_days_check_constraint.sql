-- =============================================================================
-- NOVADESK API - Drop available_days >= 0 CHECK Constraint on pr_leave_balances
-- Version  : 1.81
-- Created  : 2026-06-09
-- Purpose  : The CHECK (available_days >= 0) constraint on pr_leave_balances
--            prevents the earned leave borrowing mechanism from working.
--            When an employee borrows against future accruals (isEarned=true),
--            available_days = earnedDays - usedDays - pendingDays can legally
--            go negative. The constraint was originally defined in V1.35
--            before the LeavePolicy/borrowing rule engine was introduced.
--
--            This migration drops the constraint so that approved leave
--            requests against earned leave policies with a borrow_multiple > 0
--            can persist negative available_days. The negative balance is
--            automatically repaid through future monthly accruals (future
--            earnedDays increments offset the negative value).
-- =============================================================================

ALTER TABLE af_novadesk.pr_leave_balances
    DROP CONSTRAINT IF EXISTS pr_leave_balances_available_days_check;

COMMENT ON COLUMN af_novadesk.pr_leave_balances.available_days IS
    'Computed: earnedDays - usedDays - pendingDays for earned policies; totalAllocated - usedDays - pendingDays for upfront policies. May go negative when borrowing against future accruals on earned leave policies.';
