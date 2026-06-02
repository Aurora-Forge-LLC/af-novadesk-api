-- =============================================================================
-- NOVADESK API - Create pr_leave_balances Table
-- Version  : 1.35
-- Created  : 2026-06-01
-- Purpose  : Employee leave balance tracking per leave type and fiscal year.
--            Created automatically on employee onboarding. Aligned to the
--            entity's fiscal year setting for reset boundaries.
--            (LLR-PAY-01.1)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_leave_balances (
    id                     UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id        UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)      ON DELETE RESTRICT,
    employee_id            UUID           NOT NULL REFERENCES af_novadesk.pr_employees(id)         ON DELETE RESTRICT,
    leave_type             VARCHAR(20)    NOT NULL,
    total_allocated        DECIMAL(5,1)   NOT NULL CHECK (total_allocated >= 0),
    used_days              DECIMAL(5,1)   NOT NULL DEFAULT 0 CHECK (used_days >= 0),
    pending_days           DECIMAL(5,1)   NOT NULL DEFAULT 0 CHECK (pending_days >= 0),
    available_days         DECIMAL(5,1)   NOT NULL CHECK (available_days >= 0),
    accrual_start_date     DATE           NOT NULL,
    fiscal_year_setting_id UUID           NOT NULL REFERENCES af_novadesk.fiscal_year_settings(id) ON DELETE RESTRICT,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_lb_employee_type_fiscal
        UNIQUE (employee_id, leave_type, fiscal_year_setting_id),
    CONSTRAINT chk_lb_leave_type
        CHECK (leave_type IN ('PAID','SICK','UNPAID')),
    CONSTRAINT chk_lb_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_lb_employee_id
    ON af_novadesk.pr_leave_balances (employee_id);

CREATE INDEX IF NOT EXISTS idx_lb_entity_id
    ON af_novadesk.pr_leave_balances (legal_entity_id);

COMMENT ON TABLE af_novadesk.pr_leave_balances IS
    'Employee leave balance per leave type and fiscal year (LLR-PAY-01.1).';

COMMENT ON COLUMN af_novadesk.pr_leave_balances.available_days IS
    'Computed: total_allocated - used_days - pending_days. Stored for query performance.';
