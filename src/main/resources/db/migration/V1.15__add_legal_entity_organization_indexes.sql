-- =============================================================================
-- NOVADESK API - Add organization_id indexes & constraints to legal_entities
-- Version  : 1.15
-- Created  : 2026-05-20
-- Purpose  : Adds the indexes and unique constraints that were originally
--            added to V1.13 after it had already been applied to the database.
--            This migration is safe to run after a Flyway repair.
-- =============================================================================

SET search_path TO af_novadesk;

-- Index for organization_id — every service query filters by it
CREATE INDEX IF NOT EXISTS idx_legal_entities_organization_id
    ON af_novadesk.legal_entities(organization_id);

-- Unique constraint: entity name must be unique within an organization
-- (existing uk_legal_entity_name is global; this adds org-scoped enforcement)
CREATE UNIQUE INDEX IF NOT EXISTS idx_legal_entities_name_org
    ON af_novadesk.legal_entities(entity_name, organization_id);

-- Unique constraint: entity code must be unique within an organization
-- (existing uk_legal_entity_code is global; this adds org-scoped enforcement)
CREATE UNIQUE INDEX IF NOT EXISTS idx_legal_entities_code_org
    ON af_novadesk.legal_entities(entity_code, organization_id);
