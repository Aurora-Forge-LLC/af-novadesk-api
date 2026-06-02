-- =============================================================================
-- NOVADESK API - Create pr_leave_transactions Table
-- Version  : 1.37
-- Created  : 2026-06-01
-- Purpose  : Immutable audit trail for every leave balance change.
--            Created on: approval (deduction), cancellation (restoration),
--            and initial allocation (alloc).
--            (LLR-PAY-01.4)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_leave_transactions (
    id                 UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    leave_request_id   UUID           REFERENCES af_novadesk.pr_leave_requests(id)                ON DELETE SET NULL,
    legal_entity_id    UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)           ON DELETE RESTRICT,
    employee_id        UUID           NOT NULL REFERENCES af_novadesk.pr_employees(id)              ON DELETE RESTRICT,
    leave_type         VARCHAR(20)    NOT NULL,
    days_change        DECIMAL(4,1)   NOT NULL,
    balance_before     DECIMAL(5,1)   NOT NULL CHECK (balance_before >= 0),
    balance_after      DECIMAL(5,1)   NOT NULL CHECK (balance_after >= 0),
    transaction_type   VARCHAR(30)    NOT NULL,
    description        VARCHAR(500),
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_lt_leave_type
        CHECK (leave_type IN ('PAID','SICK','UNPAID')),
    CONSTRAINT chk_lt_transaction_type
        CHECK (transaction_type IN ('DEDUCTION','RESTORATION','ALLOCATION')),
    CONSTRAINT chk_lt_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_lt_request_id
    ON af_novadesk.pr_leave_transactions (leave_request_id);

CREATE INDEX IF NOT EXISTS idx_lt_employee_date
    ON af_novadesk.pr_leave_transactions (employee_id, created_at DESC);

COMMENT ON TABLE af_novadesk.pr_leave_transactions IS
    'Immutable audit trail for leave balance changes (LLR-PAY-01.4).';
