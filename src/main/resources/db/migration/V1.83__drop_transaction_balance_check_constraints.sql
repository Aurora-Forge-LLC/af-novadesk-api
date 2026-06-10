-- =============================================================================
-- NOVADESK API - Drop balance_before/after CHECK Constraints on pr_leave_transactions
-- Version  : 1.82
-- Created  : 2026-06-09
-- Purpose  : The CHECK (balance_before >= 0) and CHECK (balance_after >= 0)
--            constraints on pr_leave_transactions prevent recording accurate
--            audit trail entries when earned leave borrowing results in
--            negative available balances.
--
--            When an employee borrows against future accruals, the balance
--            before/after state includes negative values that must be
--            faithfully recorded for audit purposes.
--            These constraints were originally defined in V1.37 before the
--            LeavePolicy/borrowing rule engine was introduced (V1.77+).
-- =============================================================================

ALTER TABLE af_novadesk.pr_leave_transactions
    DROP CONSTRAINT IF EXISTS pr_leave_transactions_balance_before_check;

ALTER TABLE af_novadesk.pr_leave_transactions
    DROP CONSTRAINT IF EXISTS pr_leave_transactions_balance_after_check;

COMMENT ON COLUMN af_novadesk.pr_leave_transactions.balance_before IS
    'Balance before the transaction. May be negative when earned leave borrowing is in effect.';

COMMENT ON COLUMN af_novadesk.pr_leave_transactions.balance_after IS
    'Balance after the transaction. May be negative when earned leave borrowing is in effect.';
