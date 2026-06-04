-- =============================================================================
-- NOVADESK API - Create ast_write_offs Table
-- Version  : 1.61
-- Created  : 2026-06-02
-- Purpose  : Executive override records for lost or unrecoverable assets
--            during employee offboarding. Finance Manager or C-level executive
--            reviews and approves one of three actions: WRITE_OFF, DEDUCT_FROM_PAY,
--            or REQUIRE_REIMBURSEMENT. Once approved, the offboarding gate is
--            unblocked for the employee.
--            (LLR-AST-03.5)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_write_offs (
    id                   UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id             UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    organization_id      UUID            NOT NULL,

    -- Request
    requested_by         UUID            NOT NULL,
    last_custodian_id    UUID            NOT NULL,
    reason               VARCHAR(1000)   NOT NULL,
    depreciated_value    DECIMAL(19,4)   NOT NULL,

    -- Decision
    action               VARCHAR(30),
    approved_by          UUID,
    approved_at          TIMESTAMPTZ,
    status               VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    audit_notes          VARCHAR(1000),

    -- Audit
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ast_write_off_asset  UNIQUE (asset_id),
    CONSTRAINT ck_ast_wo_action        CHECK  (action IS NULL OR action IN ('WRITE_OFF','DEDUCT_FROM_PAY','REQUIRE_REIMBURSEMENT')),
    CONSTRAINT ck_ast_wo_status        CHECK  (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_ast_wo_depr_value    CHECK  (depreciated_value >= 0)
);

CREATE INDEX idx_ast_wo_asset   ON af_novadesk.ast_write_offs (asset_id);
CREATE INDEX idx_ast_wo_org     ON af_novadesk.ast_write_offs (organization_id, status);

COMMENT ON TABLE  af_novadesk.ast_write_offs                    IS 'Executive override for lost/unrecoverable assets during offboarding (LLR-AST-03.5).';
COMMENT ON COLUMN af_novadesk.ast_write_offs.requested_by       IS 'FK to cm_employees.id — IT admin raising the write-off request.';
COMMENT ON COLUMN af_novadesk.ast_write_offs.last_custodian_id  IS 'FK to cm_employees.id — employee who last held the asset.';
COMMENT ON COLUMN af_novadesk.ast_write_offs.approved_by        IS 'FK to cm_employees.id — Finance Manager or C-level executive.';
COMMENT ON COLUMN af_novadesk.ast_write_offs.depreciated_value  IS 'Net book value at time of write-off request — used for DEDUCT_FROM_PAY calculation.';
