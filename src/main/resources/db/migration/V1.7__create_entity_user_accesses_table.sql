-- =============================================================================
-- NOVADESK API - Create entity_user_accesses Table
-- Version  : 1.7
-- Created  : 2026-05-15
-- Purpose  : Create the entity_user_accesses table for granting users access
--            to specific legal entities with role-based access control.
--
-- Table    : af_novadesk.entity_user_accesses
-- =============================================================================

-- Create entity_user_accesses table
CREATE TABLE IF NOT EXISTS af_novadesk.entity_user_accesses (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    shadow_user_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    entity_role VARCHAR(50) NOT NULL,
    last_accessed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_entity_user_access UNIQUE (shadow_user_id, legal_entity_id),
    CONSTRAINT fk_entity_access_shadow_user FOREIGN KEY (shadow_user_id)
        REFERENCES af_novadesk.shadow_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_entity_access_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_entity_access_shadow_user ON af_novadesk.entity_user_accesses (shadow_user_id);
CREATE INDEX IF NOT EXISTS idx_entity_access_legal_entity ON af_novadesk.entity_user_accesses (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_entity_access_last_accessed ON af_novadesk.entity_user_accesses (shadow_user_id, last_accessed_at);

-- Add comments
COMMENT ON TABLE af_novadesk.entity_user_accesses IS 'Access grants linking users to specific legal entities';
COMMENT ON COLUMN af_novadesk.entity_user_accesses.shadow_user_id IS 'Reference to the shadow user being granted access';
COMMENT ON COLUMN af_novadesk.entity_user_accesses.legal_entity_id IS 'Reference to the legal entity being accessed';
COMMENT ON COLUMN af_novadesk.entity_user_accesses.entity_role IS 'Role within entity context: VIEWER, EDITOR, APPROVER, ADMIN';
COMMENT ON COLUMN af_novadesk.entity_user_accesses.last_accessed_at IS 'Timestamp of most recent entity context switch (audit trail)';

