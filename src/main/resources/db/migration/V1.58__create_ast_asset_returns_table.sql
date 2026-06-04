-- =============================================================================
-- NOVADESK API - Create ast_asset_returns Table
-- Version  : 1.58
-- Created  : 2026-06-02
-- Purpose  : Records the physical return of an asset from an employee to the
--            IT department. Captures condition at return, repair flag, and
--            who received it. Linked to the originating assignment.
--            (LLR-AST-03.3)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_asset_returns (
    id                       UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id                 UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    assignment_id            UUID            NOT NULL REFERENCES af_novadesk.ast_asset_assignments(id) ON DELETE RESTRICT,
    organization_id          UUID            NOT NULL,

    -- Return details
    return_date              DATE            NOT NULL,
    returned_by_employee_id  UUID            NOT NULL,
    received_by_employee_id  UUID            NOT NULL,
    condition_at_return      VARCHAR(10)     NOT NULL,
    repair_required          BOOLEAN         NOT NULL DEFAULT FALSE,
    notes                    VARCHAR(1000),

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ast_return_assignment  UNIQUE (assignment_id),
    CONSTRAINT ck_ast_return_condition   CHECK  (condition_at_return IN ('GOOD','FAIR','POOR','DAMAGED','LOST'))
);

CREATE INDEX idx_ast_returns_asset      ON af_novadesk.ast_asset_returns (asset_id);
CREATE INDEX idx_ast_returns_org        ON af_novadesk.ast_asset_returns (organization_id);

COMMENT ON TABLE  af_novadesk.ast_asset_returns                           IS 'Asset return records (LLR-AST-03.3).';
COMMENT ON COLUMN af_novadesk.ast_asset_returns.returned_by_employee_id   IS 'FK to cm_employees.id — employee returning the asset.';
COMMENT ON COLUMN af_novadesk.ast_asset_returns.received_by_employee_id   IS 'FK to cm_employees.id — IT admin receiving the asset.';
COMMENT ON COLUMN af_novadesk.ast_asset_returns.repair_required           IS 'Flags the asset for repair queue when condition is POOR or DAMAGED.';
