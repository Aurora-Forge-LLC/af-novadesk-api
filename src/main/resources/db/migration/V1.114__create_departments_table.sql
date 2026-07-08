-- =============================================================================
-- NOVADESK API - Create departments Table
-- Version  : 1.111
-- Purpose  : Create the departments table used to scope employee/user creation
--            to a fixed, seeded set of departments (IT, HR, Finance) instead of
--            free text. A department with legal_entity_id NULL is an org-level
--            department; one with legal_entity_id set is scoped to that entity.
--
-- Table    : af_novadesk.departments
-- =============================================================================

CREATE TABLE IF NOT EXISTS af_novadesk.departments (
    id               UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    organization_id  UUID NOT NULL,
    legal_entity_id  UUID REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

-- One department name per org at org-level scope (legal_entity_id IS NULL) ...
CREATE UNIQUE INDEX IF NOT EXISTS uk_departments_org_scope
    ON af_novadesk.departments (organization_id, name) WHERE legal_entity_id IS NULL;

-- ... and one department name per entity at entity-level scope.
CREATE UNIQUE INDEX IF NOT EXISTS uk_departments_entity_scope
    ON af_novadesk.departments (legal_entity_id, name) WHERE legal_entity_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_departments_organization_id ON af_novadesk.departments (organization_id);
CREATE INDEX IF NOT EXISTS idx_departments_legal_entity_id ON af_novadesk.departments (legal_entity_id);

COMMENT ON TABLE af_novadesk.departments IS 'Seeded department reference list, scoped to an organization (legal_entity_id NULL) or a specific legal entity';
COMMENT ON COLUMN af_novadesk.departments.legal_entity_id IS 'NULL = org-level department; set = entity-level department scoped to that legal entity';
