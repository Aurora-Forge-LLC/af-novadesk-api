-- =============================================================================
-- NOVADESK API - Create pr_payslips Table
-- Version  : 1.42
-- Created  : 2026-06-01
-- Purpose  : Individual employee payslip. Generated as a child of a payroll
--            batch. Contains attendance summary and financial breakdown.
--            Dynamic line items stored in pr_payslip_line_items.
--            (LLR-PAY-02.5, PAY-04)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payslips (
    id                  UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    payroll_batch_id    UUID           NOT NULL REFERENCES af_novadesk.pr_payroll_batches(id)  ON DELETE CASCADE,
    legal_entity_id     UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)      ON DELETE RESTRICT,
    employee_id         UUID           NOT NULL REFERENCES af_novadesk.pr_employees(id)         ON DELETE RESTRICT,
    pay_period_start    DATE           NOT NULL,
    pay_period_end      DATE           NOT NULL,
    payment_date        DATE           NOT NULL,
    total_working_days  INTEGER        NOT NULL CHECK (total_working_days > 0),
    days_worked         DECIMAL(4,1)   NOT NULL CHECK (days_worked >= 0),
    paid_leave_days     DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (paid_leave_days >= 0),
    sick_leave_days     DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (sick_leave_days >= 0),
    unpaid_leave_days   DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (unpaid_leave_days >= 0),
    gross_salary        DECIMAL(19,4)  NOT NULL CHECK (gross_salary >= 0),
    total_deductions    DECIMAL(19,4)  NOT NULL CHECK (total_deductions >= 0),
    net_salary          DECIMAL(19,4)  NOT NULL CHECK (net_salary >= 0),
    ytd_gross_earnings  DECIMAL(19,4),
    ytd_taxes           DECIMAL(19,4),
    currency_code       CHAR(3)        NOT NULL,
    payslip_pdf_path    VARCHAR(500),
    is_downloaded       BOOLEAN        NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ps_batch_employee
        UNIQUE (payroll_batch_id, employee_id),
    CONSTRAINT chk_ps_period
        CHECK (pay_period_end >= pay_period_start),
    CONSTRAINT chk_ps_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_ps_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_ps_employee_period
    ON af_novadesk.pr_payslips (employee_id, pay_period_start DESC);

CREATE INDEX IF NOT EXISTS idx_ps_entity_period
    ON af_novadesk.pr_payslips (legal_entity_id, pay_period_start DESC);

COMMENT ON TABLE af_novadesk.pr_payslips IS
    'Employee payslip record (LLR-PAY-02.5, PAY-04). Dynamic line items in pr_payslip_line_items.';
