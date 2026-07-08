-- =============================================================================
-- NOVADESK API - Create ast_payroll_deductions Table
-- Version  : 1.111
-- Purpose  : Records irrevocable payroll deductions created when a write-off
--            is approved with action DEDUCT_FROM_PAY. Picked up by the payroll
--            batch job for whichever period covers the deduction_date.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_payroll_deductions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    write_off_id        UUID            NOT NULL
                            REFERENCES af_novadesk.ast_write_offs(id) ON DELETE RESTRICT,
    organization_id     UUID            NOT NULL,
    employee_id         UUID            NOT NULL,

    amount              DECIMAL(19,4)   NOT NULL,
    currency_code       VARCHAR(3)      NOT NULL,
    deduction_date      DATE            NOT NULL,
    write_off_reason    VARCHAR(20)     NOT NULL,
    asset_label         VARCHAR(300)    NOT NULL,
    deduction_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING',

    payroll_batch_id    UUID,
    payslip_id          UUID,

    record_status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ast_pd_write_off
        UNIQUE (write_off_id),

    CONSTRAINT ck_ast_pd_amount
        CHECK (amount >= 0),

    CONSTRAINT ck_ast_pd_write_off_reason
        CHECK (write_off_reason IN ('DAMAGED','LOST','RETIRED','OTHER')),

    CONSTRAINT ck_ast_pd_deduction_status
        CHECK (deduction_status IN ('PENDING','APPLIED')),

    CONSTRAINT ck_ast_pd_record_status
        CHECK (record_status IN ('ACTIVE','DELETED'))
);

CREATE INDEX idx_ast_pd_org_emp_date_status
    ON af_novadesk.ast_payroll_deductions (organization_id, employee_id, deduction_date, record_status);

CREATE INDEX idx_ast_pd_write_off
    ON af_novadesk.ast_payroll_deductions (write_off_id);

COMMENT ON TABLE af_novadesk.ast_payroll_deductions IS
    'Irrevocable payroll deductions for DAMAGED or LOST assets approved with DEDUCT_FROM_PAY. Applied by the payroll batch for the period covering deduction_date.';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.write_off_id IS
    'FK to ast_write_offs.id — one deduction record per approved write-off (enforced by unique constraint).';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.employee_id IS
    'FK to cm_employees.id — the last custodian responsible for the asset.';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.amount IS
    'Net book value of the asset at write-off approval time.';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.deduction_date IS
    'Date the deduction was decided (write-off approval date). Payroll picks up all PENDING deductions whose deduction_date falls within the batch pay period.';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.asset_label IS
    'Denormalised label (assetType + serial number) used in the payslip line item description.';

COMMENT ON COLUMN af_novadesk.ast_payroll_deductions.deduction_status IS
    'PENDING = awaiting payroll batch; APPLIED = included in a payslip.';
