-- =============================================================================
-- NOVADESK API - Create ast_asset_assignments Table
-- Version  : 1.57
-- Created  : 2026-06-02
-- Purpose  : Records the assignment of an asset to an employee including the
--            digital acknowledgment workflow. Contains all business logic for
--            the assignment lifecycle (pending ack → assigned → returned/transferred).
--            Separate from ast_custody_transfers which is the immutable audit log.
--            (LLR-AST-02)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_asset_assignments (
    id                      UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id                UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    employee_id             UUID            NOT NULL,
    assigned_by             UUID            NOT NULL,
    organization_id         UUID            NOT NULL,

    -- Assignment details
    assignment_date         DATE            NOT NULL,
    expected_return_date    DATE,
    purpose                 VARCHAR(30)     NOT NULL DEFAULT 'PRIMARY_WORK',
    condition_at_assignment VARCHAR(10)     NOT NULL DEFAULT 'GOOD',

    -- Acknowledgment workflow
    requires_acknowledgment BOOLEAN         NOT NULL DEFAULT TRUE,
    acknowledgment_status   VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    acknowledgment_at       TIMESTAMPTZ,
    acknowledgment_ip       VARCHAR(50),
    acknowledgment_token    VARCHAR(200),
    acknowledgment_token_expires_at TIMESTAMPTZ,

    -- Lifecycle
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    notes                   VARCHAR(500),

    -- Audit
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ast_asgn_purpose    CHECK (purpose IN ('PRIMARY_WORK','BACKUP','PROJECT','TESTING')),
    CONSTRAINT ck_ast_asgn_condition  CHECK (condition_at_assignment IN ('NEW','GOOD','FAIR','POOR')),
    CONSTRAINT ck_ast_asgn_ack_status CHECK (acknowledgment_status IN ('PENDING','ACKNOWLEDGED','WAIVED')),
    CONSTRAINT ck_ast_asgn_status     CHECK (status IN ('ACTIVE','RETURNED','TRANSFERRED'))
);

CREATE INDEX idx_ast_asgn_asset       ON af_novadesk.ast_asset_assignments (asset_id);
CREATE INDEX idx_ast_asgn_employee    ON af_novadesk.ast_asset_assignments (employee_id);
CREATE INDEX idx_ast_asgn_org_status  ON af_novadesk.ast_asset_assignments (organization_id, status);
CREATE INDEX idx_ast_asgn_ack_token   ON af_novadesk.ast_asset_assignments (acknowledgment_token)
    WHERE acknowledgment_token IS NOT NULL;

COMMENT ON TABLE  af_novadesk.ast_asset_assignments                        IS 'Asset assignment business records with acknowledgment workflow (LLR-AST-02).';
COMMENT ON COLUMN af_novadesk.ast_asset_assignments.acknowledgment_token   IS 'One-time token sent to employee via email for acknowledgment page.';
COMMENT ON COLUMN af_novadesk.ast_asset_assignments.employee_id            IS 'FK to cm_employees.id — the employee receiving the asset.';
COMMENT ON COLUMN af_novadesk.ast_asset_assignments.assigned_by            IS 'FK to cm_employees.id — the IT admin performing the assignment.';
