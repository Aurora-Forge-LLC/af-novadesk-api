-- =============================================================================
-- NOVADESK API - Create pr_leave_requests Table
-- Version  : 1.36
-- Created  : 2026-06-01
-- Purpose  : Leave request aggregate root. Tracks employee leave requests
--            through the approval hierarchy:
--            Manager → HR (>5 consecutive days) → Executive (Unpaid leaves).
--            (LLR-PAY-01.2–01.5)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_leave_requests (
    id                     UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id        UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)      ON DELETE RESTRICT,
    employee_id            UUID           NOT NULL REFERENCES af_novadesk.pr_employees(id)         ON DELETE RESTRICT,
    leave_type             VARCHAR(20)    NOT NULL,
    start_date             DATE           NOT NULL,
    end_date               DATE           NOT NULL,
    number_of_days         DECIMAL(4,1)   NOT NULL CHECK (number_of_days > 0),
    reason                 VARCHAR(500),
    attachment_path        VARCHAR(500),
    paid_balance_before    DECIMAL(5,1)   NOT NULL CHECK (paid_balance_before >= 0),
    sick_balance_before    DECIMAL(5,1)   NOT NULL CHECK (sick_balance_before >= 0),
    paid_days_used         DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (paid_days_used >= 0),
    sick_days_used         DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (sick_days_used >= 0),
    unpaid_days_used       DECIMAL(4,1)   NOT NULL DEFAULT 0 CHECK (unpaid_days_used >= 0),
    approver_id            UUID           REFERENCES af_novadesk.pr_employees(id)                  ON DELETE SET NULL,
    second_approver_id     UUID           REFERENCES af_novadesk.pr_employees(id)                  ON DELETE SET NULL,
    executive_approver_id  UUID           REFERENCES af_novadesk.pr_employees(id)                  ON DELETE SET NULL,
    leave_request_status   VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    approver_comment       VARCHAR(500),
    submitted_at           TIMESTAMPTZ,
    approved_at            TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ,
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_lr_dates
        CHECK (end_date >= start_date),
    CONSTRAINT chk_lr_leave_type
        CHECK (leave_type IN ('PAID','SICK','UNPAID')),
    CONSTRAINT chk_lr_status
        CHECK (leave_request_status IN ('PENDING','APPROVED','REJECTED','MODIFICATION_REQUESTED','CANCELLED')),
    CONSTRAINT chk_lr_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_lr_employee_date
    ON af_novadesk.pr_leave_requests (employee_id, start_date DESC);

CREATE INDEX IF NOT EXISTS idx_lr_approver_status
    ON af_novadesk.pr_leave_requests (approver_id, leave_request_status);

CREATE INDEX IF NOT EXISTS idx_lr_entity_status
    ON af_novadesk.pr_leave_requests (legal_entity_id, leave_request_status);

COMMENT ON TABLE af_novadesk.pr_leave_requests IS
    'Leave request aggregate root (LLR-PAY-01.2–01.5). Tracks full approval lifecycle.';

COMMENT ON COLUMN af_novadesk.pr_leave_requests.paid_balance_before IS
    'Snapshot of the employee''s Paid leave balance at request time for audit.';
