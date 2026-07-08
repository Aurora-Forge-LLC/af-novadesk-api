-- =============================================================================
-- NOVADESK API - Backfill default departments
-- Version  : 1.112
-- Purpose  : One-time backfill of the IT/HR/Finance departments introduced in
--            V1.111, for organizations and legal entities that already existed
--            before this feature. Going forward, seeding happens in
--            LegalEntityServiceImpl.approveEntity (DepartmentService).
--            Idempotent — safe to re-run.
-- =============================================================================

-- Org-level departments, for every organization known via legal_entities or
-- cm_employees (there is no local organizations table in this service).
INSERT INTO af_novadesk.departments (id, organization_id, legal_entity_id, name, created_at, updated_at, status)
SELECT public.gen_random_uuid(), orgs.org_id, NULL, defaults.dept_name, now(), now(), 'ACTIVE'
FROM (
    SELECT DISTINCT organization_id AS org_id FROM af_novadesk.legal_entities WHERE organization_id IS NOT NULL
    UNION
    SELECT DISTINCT organization_id AS org_id FROM af_novadesk.cm_employees WHERE organization_id IS NOT NULL
) orgs
CROSS JOIN (VALUES ('IT'), ('HR'), ('Finance')) AS defaults(dept_name)
ON CONFLICT (organization_id, name) WHERE legal_entity_id IS NULL DO NOTHING;

-- Entity-level departments, for every existing legal entity.
INSERT INTO af_novadesk.departments (id, organization_id, legal_entity_id, name, created_at, updated_at, status)
SELECT public.gen_random_uuid(), le.organization_id, le.id, defaults.dept_name, now(), now(), 'ACTIVE'
FROM af_novadesk.legal_entities le
CROSS JOIN (VALUES ('IT'), ('HR'), ('Finance')) AS defaults(dept_name)
WHERE le.organization_id IS NOT NULL
ON CONFLICT (legal_entity_id, name) WHERE legal_entity_id IS NOT NULL DO NOTHING;
