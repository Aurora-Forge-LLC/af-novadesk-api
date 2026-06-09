-- =============================================================================
-- NOVADESK API - Make leave_type Nullable in pr_leave_balances
-- Version  : 1.80
-- Created  : 2026-06-08
-- Purpose  : leave_type is deprecated in favor of leave_policy_id (V1.78).
--            Making it nullable so that new LeaveBalance records created via
--            the LeavePolicy rule engine do not require the legacy field.
-- =============================================================================

ALTER TABLE af_novadesk.pr_leave_balances
    ALTER COLUMN leave_type DROP NOT NULL;

COMMENT ON COLUMN af_novadesk.pr_leave_balances.leave_type IS
    'DEPRECATED: Use leave_policy_id instead. Retained for backward compatibility with legacy records.';
