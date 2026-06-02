-- =============================================================================
-- NOVADESK API - Create pr_payroll_batches Table
-- Version  : 1.39
-- Created  : 2026-06-01
-- Purpose  : Payroll batch aggregate root. Represents a single payroll run
--            for a legal entity within a pay period.
--            (LLR-PAY-02, PAY-03)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payroll_batches (
    id                    UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id       UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)      ON DELETE RESTRICT,
    pay_period_start      DATE           NOT NULL,
    pay_period_end        DATE           NOT NULL,
    payment_date          DATE           NOT NULL,
    total_headcount       INTEGER        NOT NULL CHECK (total_headcount >= 0),
    processed_count       INTEGER        NOT NULL CHECK (processed_count >= 0),
    flagged_count         INTEGER        NOT NULL DEFAULT 0 CHECK (flagged_count >= 0),
    total_gross_salary    DECIMAL(19,4)  NOT NULL DEFAULT 0 CHECK (total_gross_salary >= 0),
    total_deductions      DECIMAL(19,4)  NOT NULL DEFAULT 0 CHECK (total_deductions >= 0),
    total_net_payout      DECIMAL(19,4)  NOT NULL DEFAULT 0 CHECK (total_net_payout >= 0),
    currency_code         CHAR(3)        NOT NULL,
    exchange_rate_usd     DECIMAL(19,6),
    total_net_payout_usd  DECIMAL(19,4),
    batch_status          VARCHAR(30)    NOT NULL DEFAULT 'INITIATED',
    approved_by           UUID           REFERENCES af_novadesk.pr_employees(id)                  ON DELETE SET NULL,
    approved_at           TIMESTAMPTZ,
    rejection_reason      VARCHAR(500),
    journal_id            UUID,
    ledger_posted_at      TIMESTAMPTZ,
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pb_dates
        CHECK (pay_period_end >= pay_period_start),
    CONSTRAINT chk_pb_batch_status
        CHECK (batch_status IN ('INITIATED','UNDER_REVIEW','APPROVED',
                                'APPROVED_PENDING_PAYMENT','REJECTED','VOIDED')),
    CONSTRAINT chk_pb_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_pb_currency
        CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX IF NOT EXISTS idx_pb_entity_period
    ON af_novadesk.pr_payroll_batches (legal_entity_id, pay_period_start DESC);

CREATE INDEX IF NOT EXISTS idx_pb_status_created
    ON af_novadesk.pr_payroll_batches (batch_status, created_at DESC);

COMMENT ON TABLE af_novadesk.pr_payroll_batches IS
    'Payroll batch aggregate root (LLR-PAY-02). Tracks full payroll run lifecycle.';

COMMENT ON COLUMN af_novadesk.pr_payroll_batches.journal_id IS
    'UUID that groups all PayrollLedgerEntry rows for this batch (LLR-PAY-03).';
