-- =============================================================================
-- NOVADESK API - Common Employee Entity Assignments + fix cm_employees
-- Version  : 1.71
-- Created  : 2026-06-05
-- Purpose  :
--   1. Add record_status to cm_employees — separates the AbstractEntity
--      lifecycle status from the employee-domain status (ACTIVE/INACTIVE/OFFBOARDING)
--      following the same pattern used in V1.65 for asset tables.
--   2. Create cm_employee_entity_assignments — bridge table establishing the
--      many-to-many relationship between employees and legal entities within
--      the same organization. An employee may work across multiple entities;
--      exactly one assignment is marked is_primary_entity = true and carries
--      the canonical payroll entity reference.
-- =============================================================================

SET search_path TO af_novadesk;

-- ── 1. cm_employees — add record_status ──────────────────────────────────────

ALTER TABLE af_novadesk.cm_employees
    ADD COLUMN IF NOT EXISTS record_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- ── 2. cm_employee_entity_assignments ────────────────────────────────────────

CREATE TABLE IF NOT EXISTS af_novadesk.cm_employee_entity_assignments (
    id                UUID        PRIMARY KEY DEFAULT public.gen_random_uuid(),
    employee_id       UUID        NOT NULL REFERENCES af_novadesk.cm_employees(id) ON DELETE CASCADE,
    legal_entity_id   UUID        NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    organization_id   UUID        NOT NULL,

    department        VARCHAR(100),
    designation       VARCHAR(100),
    is_primary_entity BOOLEAN     NOT NULL DEFAULT FALSE,

    hire_date         DATE        NOT NULL,
    termination_date  DATE,

    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    record_status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_cm_emp_entity    UNIQUE (employee_id, legal_entity_id),
    CONSTRAINT ck_cm_ea_status     CHECK  (status IN ('ACTIVE','INACTIVE','TERMINATED'))
);

-- Lookup by employee (list all entities an employee is assigned to)
CREATE INDEX IF NOT EXISTS idx_cm_ea_employee
    ON af_novadesk.cm_employee_entity_assignments (employee_id);

-- Lookup by entity (list all employees working for an entity)
CREATE INDEX IF NOT EXISTS idx_cm_ea_entity
    ON af_novadesk.cm_employee_entity_assignments (legal_entity_id, status);

-- Org-scoped queries
CREATE INDEX IF NOT EXISTS idx_cm_ea_org
    ON af_novadesk.cm_employee_entity_assignments (organization_id);

-- Fast lookup of primary entity assignment per employee
CREATE UNIQUE INDEX IF NOT EXISTS idx_cm_ea_primary
    ON af_novadesk.cm_employee_entity_assignments (employee_id)
    WHERE is_primary_entity = TRUE;

COMMENT ON TABLE  af_novadesk.cm_employee_entity_assignments IS
    'Bridge table — many-to-many between cm_employees and legal_entities. '
    'One row per (employee, entity) pair. is_primary_entity marks the entity '
    'that processes this employee''s payroll.';

COMMENT ON COLUMN af_novadesk.cm_employee_entity_assignments.is_primary_entity IS
    'TRUE for the single entity that owns this employee''s payroll. '
    'Enforced unique per employee via partial unique index.';
