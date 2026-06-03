-- =============================================================================
-- NOVADESK API - Create ast_custody_transfers Table
-- Version  : 1.59
-- Created  : 2026-06-02
-- Purpose  : Immutable append-only audit log of every custodian change for
--            an asset. A new row is inserted for every assignment, return,
--            and reassignment — rows are never updated or deleted.
--            Provides a complete chain of custody for compliance/audit.
--            (LLR-AST-02.3)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_custody_transfers (
    id                   UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id             UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    organization_id      UUID            NOT NULL,

    -- Custodian (before)
    from_custodian_type  VARCHAR(20)     NOT NULL,
    from_custodian_id    UUID,

    -- Custodian (after)
    to_custodian_type    VARCHAR(20)     NOT NULL,
    to_custodian_id      UUID,

    -- Transfer context
    transfer_type        VARCHAR(20)     NOT NULL,
    transfer_date        DATE            NOT NULL,
    approved_by          UUID            NOT NULL,
    notes                VARCHAR(500),

    -- Immutable audit timestamp
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ast_ct_from_type  CHECK (from_custodian_type IN ('EMPLOYEE','IT_DEPARTMENT')),
    CONSTRAINT ck_ast_ct_to_type    CHECK (to_custodian_type   IN ('EMPLOYEE','IT_DEPARTMENT')),
    CONSTRAINT ck_ast_ct_type       CHECK (transfer_type        IN ('ASSIGNMENT','RETURN','REASSIGNMENT','WRITE_OFF','DISPOSAL'))
);

CREATE INDEX idx_ast_ct_asset  ON af_novadesk.ast_custody_transfers (asset_id);
CREATE INDEX idx_ast_ct_org    ON af_novadesk.ast_custody_transfers (organization_id);

COMMENT ON TABLE  af_novadesk.ast_custody_transfers                IS 'Immutable chain-of-custody audit log — never updated, only inserted (LLR-AST-02.3).';
COMMENT ON COLUMN af_novadesk.ast_custody_transfers.from_custodian_id IS 'cm_employees.id or NULL when source is IT_DEPARTMENT.';
COMMENT ON COLUMN af_novadesk.ast_custody_transfers.to_custodian_id   IS 'cm_employees.id or NULL when destination is IT_DEPARTMENT.';
COMMENT ON COLUMN af_novadesk.ast_custody_transfers.approved_by       IS 'FK to cm_employees.id — IT admin who approved the transfer.';
