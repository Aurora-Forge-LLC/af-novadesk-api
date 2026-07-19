-- =============================================================================
-- NOVADESK API - Create ast_asset_reservations Table
-- Version  : 1.118
-- Purpose  : Time-boxed reservations of shared assets by employees (LLR-AST-05).
--            Employees request a window; ops approve or reject. Reservations
--            that are never decided auto-expire once their window has passed.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_asset_reservations (
    id                  UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),

    asset_id            UUID            NOT NULL
                            REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    organization_id     UUID            NOT NULL,
    requested_by        UUID            NOT NULL,

    starts_at           TIMESTAMPTZ     NOT NULL,
    ends_at             TIMESTAMPTZ     NOT NULL,
    purpose             VARCHAR(300),

    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    decided_by          UUID,
    decided_at          TIMESTAMPTZ,
    decision_notes      VARCHAR(500),

    record_status       VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_ast_rsv_window
        CHECK (ends_at > starts_at),

    CONSTRAINT ck_ast_rsv_status
        CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED','EXPIRED')),

    CONSTRAINT ck_ast_rsv_record_status
        CHECK (record_status IN ('ACTIVE','DELETED'))
);

CREATE INDEX idx_ast_rsv_asset
    ON af_novadesk.ast_asset_reservations (asset_id);

CREATE INDEX idx_ast_rsv_requester
    ON af_novadesk.ast_asset_reservations (requested_by);

CREATE INDEX idx_ast_rsv_org_status
    ON af_novadesk.ast_asset_reservations (organization_id, status);

CREATE INDEX idx_ast_rsv_asset_window
    ON af_novadesk.ast_asset_reservations (asset_id, starts_at, ends_at);

COMMENT ON TABLE af_novadesk.ast_asset_reservations IS
    'Time-boxed reservations of shared assets. Ops approve/reject; undecided reservations auto-expire once their window passes.';

COMMENT ON COLUMN af_novadesk.ast_asset_reservations.requested_by IS
    'cm_employees.id — the employee who requested the reservation.';

COMMENT ON COLUMN af_novadesk.ast_asset_reservations.decided_by IS
    'cm_employees.id — the ops user who approved or rejected the reservation.';

COMMENT ON COLUMN af_novadesk.ast_asset_reservations.status IS
    'PENDING = awaiting decision; APPROVED/REJECTED = decided; CANCELLED = withdrawn by requester; EXPIRED = window passed undecided.';
