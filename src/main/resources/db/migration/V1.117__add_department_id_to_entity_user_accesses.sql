-- =============================================================================
-- NOVADESK API - Add department_id to entity_user_accesses
-- Version  : 1.114
-- Purpose  : Capture department at entity access-grant/invite time (grantAccess/
--            inviteUser) — this path grants login/role access but does not
--            create a CmEmployee record, so department has nowhere else to live.
-- =============================================================================

ALTER TABLE af_novadesk.entity_user_accesses
    ADD COLUMN IF NOT EXISTS department_id UUID REFERENCES af_novadesk.departments(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_entity_access_department_id
    ON af_novadesk.entity_user_accesses (department_id);

COMMENT ON COLUMN af_novadesk.entity_user_accesses.department_id IS 'Department selected at grant/invite time, scoped to this entity_user_access''s legal_entity_id';
