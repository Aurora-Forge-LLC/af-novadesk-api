-- =============================================================================
-- NOVADESK API - Create pr_payroll_flagged_employees Table
-- Version  : 1.40
-- Created  : 2026-06-01
-- Purpose  : Review queue items for employees with unpaid leave or unauthorized
--            absences. Executive can Waive (full salary) or Prorate (adjusted).
--            (LLR-PAY-02.2–02.3)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payroll_flagged_employees (
    id                       UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    payroll_batch_id         UUID           NOT NULL REFERENCES af_novadesk.pr_payroll_batches(id) ON DELETE CASCADE,
    employee_id              UUID           NOT NULL REFERENCES af_novadesk.pr_employees(id)        ON DELETE RESTRICT,
    unpaid_leave_days        DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (unpaid_leave_days >= 0),
    unauthorized_absence_days DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (unauthorized_absence_days >= 0),
    flag_reason              VARCHAR(500)   NOT NULL,
    base_salary              DECIMAL(19,4)  NOT NULL CHECK (base_salary >= 0),
    calculated_salary        DECIMAL(19,4),
    total_working_days       INTEGER        NOT NULL CHECK (total_working_days > 0),
    days_worked              DECIMAL(4,1)   NOT NULL CHECK (days_worked >= 0),
    flag_action              VARCHAR(20)    NOT NULL DEFAULT 'PENDING_REVIEW',
    action_by                UUID           REFERENCES af_novadesk.pr_employees(id)                 ON DELETE SET NULL,
    action_at                TIMESTAMPTZ,
    action_reason            VARCHAR(500),
    status                   VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_pfe_batch_employee
        UNIQUE (payroll_batch_id, employee_id),
    CONSTRAINT chk_pfe_flag_action
        CHECK (flag_action IN ('PENDING_REVIEW','WAIVED','PRORATED')),
    CONSTRAINT chk_pfe_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_pfe_action_created
    ON af_novadesk.pr_payroll_flagged_employees (flag_action, created_at);

COMMENT ON TABLE af_novadesk.pr_payroll_flagged_employees IS
    'Review queue for employees flagged during payroll processing (LLR-PAY-02.2–02.3).';
