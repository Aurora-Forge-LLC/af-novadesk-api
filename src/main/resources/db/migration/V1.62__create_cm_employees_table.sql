-- =============================================================================
-- NOVADESK API - Create cm_employees Table
-- Version  : 1.62
-- Created  : 2026-06-02
-- Purpose  : Common employee identity table shared across modules (asset
--            management, offboarding, future HR integrations).
--            Holds a thin reference to the AuthHub identity layer via
--            auth_user_id. One row per person in the organisation —
--            not entity-scoped (an employee can work across multiple
--            legal entities within the same org).
--            Module-specific details (salary, leave, asset assignments)
--            live in their own tables and reference this record.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.cm_employees (
    id               UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    organization_id  UUID           NOT NULL,
    auth_user_id     UUID           NOT NULL,
    employee_code    VARCHAR(50)    NOT NULL,
    display_name     VARCHAR(200)   NOT NULL,
    email            VARCHAR(255),
    status           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_cm_emp_auth_user  UNIQUE (organization_id, auth_user_id),
    CONSTRAINT uk_cm_emp_code       UNIQUE (organization_id, employee_code),
    CONSTRAINT ck_cm_emp_status     CHECK  (status IN ('ACTIVE','INACTIVE','OFFBOARDING'))
);

CREATE INDEX IF NOT EXISTS idx_cm_emp_org       ON af_novadesk.cm_employees (organization_id);
CREATE INDEX IF NOT EXISTS idx_cm_emp_auth_user ON af_novadesk.cm_employees (auth_user_id);
CREATE INDEX IF NOT EXISTS idx_cm_emp_status    ON af_novadesk.cm_employees (organization_id, status);

COMMENT ON TABLE  af_novadesk.cm_employees              IS 'Common employee identity — one row per person, shared by all modules.';
COMMENT ON COLUMN af_novadesk.cm_employees.auth_user_id IS 'FK to AuthHub shadow_users.auth_user_id — the canonical identity.';
COMMENT ON COLUMN af_novadesk.cm_employees.status       IS 'ACTIVE | INACTIVE | OFFBOARDING';
