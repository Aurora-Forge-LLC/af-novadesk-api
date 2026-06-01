-- =============================================================================
-- NOVADESK API - Create pr_payroll_audit_logs Table
-- Version  : 1.48
-- Created  : 2026-06-01
-- Purpose  : Immutable audit log for all state transitions across the payroll
--            module (waive/prorate actions, tax config changes, etc.).
--            Stores a JSON snapshot of the entity state before the change.
--            (LLR-PAY-02.3, PAY-04.6)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payroll_audit_logs (
    id                UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    organization_id   UUID           NOT NULL,
    action            VARCHAR(30)    NOT NULL,
    entity_type       VARCHAR(50)    NOT NULL,
    entity_id         UUID           NOT NULL,
    performed_by      UUID           REFERENCES af_novadesk.pr_employees(id)  ON DELETE SET NULL,
    details           VARCHAR(1000),
    change_snapshot   JSONB,
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pal_action
        CHECK (action IN ('LEAVE_REQUESTED','LEAVE_APPROVED','LEAVE_REJECTED',
                           'LEAVE_MODIFIED','LEAVE_CANCELLED',
                           'PAYROLL_INITIATED','PAYROLL_APPROVED','PAYROLL_REJECTED',
                           'PAYROLL_VOIDED','FLAG_WAIVED','FLAG_PRORATED',
                           'TAX_CONFIG_UPDATED','EMPLOYEE_ONBOARDED','EMPLOYEE_TERMINATED')),
    CONSTRAINT chk_pal_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_pal_entity
    ON af_novadesk.pr_payroll_audit_logs (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_pal_created
    ON af_novadesk.pr_payroll_audit_logs (created_at DESC);

COMMENT ON TABLE af_novadesk.pr_payroll_audit_logs IS
    'Immutable audit log for payroll module state transitions (cross-cutting).';
