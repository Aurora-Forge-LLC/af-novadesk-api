-- =============================================================================
-- NOVADESK API - Add department_id to cm_employee_entity_assignments
-- Version  : 1.113
-- Purpose  : Replace the free-text `department` column with a structured FK
--            into the new departments table (V1.111). The old `department`
--            text column is kept (deprecated, unused for new writes) so
--            existing historical rows keep their value.
-- =============================================================================

ALTER TABLE af_novadesk.cm_employee_entity_assignments
    ADD COLUMN IF NOT EXISTS department_id UUID REFERENCES af_novadesk.departments(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_cm_ea_department_id
    ON af_novadesk.cm_employee_entity_assignments (department_id);

COMMENT ON COLUMN af_novadesk.cm_employee_entity_assignments.department_id IS 'FK into departments; replaces the deprecated free-text department column for new writes';
COMMENT ON COLUMN af_novadesk.cm_employee_entity_assignments.department IS 'Deprecated — historical free-text value; new writes use department_id instead';
