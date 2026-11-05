-- =============================================================================
-- NOVADESK API - Create mnt_maintenance_requests Table
-- Version  : 1.118
-- Created  : 2026-07-10
-- Purpose  : Employee-submitted asset maintenance/repair requests and the ops
--            review workflow (submit -> review -> approve/reject -> assign
--            technician -> complete).
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.mnt_maintenance_requests (
    id                      UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id                UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    requested_by            UUID            NOT NULL,
    organization_id         UUID            NOT NULL,

    description             VARCHAR(2000)   NOT NULL,
    priority                VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    cost_estimate           NUMERIC(19,4),
    assigned_technician_id  UUID,
    review_notes            VARCHAR(1000),
    resolved_at             TIMESTAMPTZ,

    -- Audit
    record_status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_mnt_req_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_mnt_req_status   CHECK (status IN ('SUBMITTED','IN_REVIEW','APPROVED','REJECTED','IN_PROGRESS','COMPLETED'))
);

CREATE INDEX idx_mnt_req_asset      ON af_novadesk.mnt_maintenance_requests (asset_id);
CREATE INDEX idx_mnt_req_requester  ON af_novadesk.mnt_maintenance_requests (requested_by);
CREATE INDEX idx_mnt_req_org_status ON af_novadesk.mnt_maintenance_requests (organization_id, status);

COMMENT ON TABLE  af_novadesk.mnt_maintenance_requests                        IS 'Employee-submitted asset maintenance/repair requests and the ops review workflow.';
COMMENT ON COLUMN af_novadesk.mnt_maintenance_requests.requested_by           IS 'FK to cm_employees.id, the employee who submitted the request.';
COMMENT ON COLUMN af_novadesk.mnt_maintenance_requests.assigned_technician_id IS 'FK to cm_employees.id, nullable until ops assigns a technician.';
