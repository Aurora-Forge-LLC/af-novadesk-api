-- =============================================================================
-- NOVADESK API - Add asset_deduction_amount to pr_payslips
-- Version  : 1.112
-- Purpose  : Tracks the total asset write-off deduction applied in a pay
--            period. Kept separate from total_deductions for auditability.
-- =============================================================================

ALTER TABLE af_novadesk.pr_payslips
    ADD COLUMN IF NOT EXISTS asset_deduction_amount DECIMAL(19,4) NOT NULL DEFAULT 0;
