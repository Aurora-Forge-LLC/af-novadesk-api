-- =============================================================================
-- NOVADESK API - Create pr_employee_outbox_events Table
-- Version  : 1.70
-- Purpose  : Transactional outbox for employee lifecycle events (onboarded,
--            updated, terminated). Consumed by the Finance module to grant/
--            update EntityUserAccess roles, and by other consumers (cm_employees
--            sync) in the future.
--
--            The idempotency_key UNIQUE constraint guarantees at-least-once
--            processing: consumers can safely retry without double-processing.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_employee_outbox_events (
    id               UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type       VARCHAR(50)    NOT NULL,
    idempotency_key  VARCHAR(255)   NOT NULL UNIQUE,
    payload          JSONB          NOT NULL,
    organization_id  UUID           NOT NULL,
    employee_id      UUID           NOT NULL,
    auth_user_id     UUID           NOT NULL,
    outbox_status    VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    retry_count      INT            NOT NULL DEFAULT 0,
    last_error       TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_emp_outbox_status
    ON af_novadesk.pr_employee_outbox_events (outbox_status, created_at);

COMMENT ON TABLE  af_novadesk.pr_employee_outbox_events
    IS 'Transactional outbox for employee lifecycle events (Payroll → cross-module consumers).';
COMMENT ON COLUMN af_novadesk.pr_employee_outbox_events.idempotency_key
    IS 'Unique key (e.g. EMPLOYEE_ONBOARDED:<employeeId>) for deduplication.';
COMMENT ON COLUMN af_novadesk.pr_employee_outbox_events.outbox_status
    IS 'PENDING | FINANCE_PROCESSED | FAILED';
