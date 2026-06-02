-- =============================================================================
-- NOVADESK API - Create pr_payslip_line_items Table
-- Version  : 1.43
-- Created  : 2026-06-01
-- Purpose  : Dynamic line items (earnings, deductions, employer expenses) on
--            a payslip. Uses a flexible code-based system so any jurisdiction's
--            tax components can be stored without schema changes.
--            (LLR-PAY-04.5)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_payslip_line_items (
    id                    UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    payslip_id            UUID           NOT NULL REFERENCES af_novadesk.pr_payslips(id)         ON DELETE CASCADE,
    line_item_type        VARCHAR(20)    NOT NULL,
    line_item_code        VARCHAR(50)    NOT NULL,
    line_item_description VARCHAR(200)   NOT NULL,
    amount                DECIMAL(19,4)  NOT NULL CHECK (amount >= 0),
    currency_code         CHAR(3)        NOT NULL,
    display_order         INTEGER,
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_pli_line_item_type
        CHECK (line_item_type IN ('EARNING','DEDUCTION','EMPLOYER_EXPENSE')),
    CONSTRAINT chk_pli_currency
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_pli_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_pli_payslip_type
    ON af_novadesk.pr_payslip_line_items (payslip_id, line_item_type);

CREATE INDEX IF NOT EXISTS idx_pli_code
    ON af_novadesk.pr_payslip_line_items (line_item_code);

COMMENT ON TABLE af_novadesk.pr_payslip_line_items IS
    'Dynamic payslip line items (LLR-PAY-04.5). Codes: NP_SSF_EMPLOYEE, IN_PF_EMPLOYEE, etc.';
