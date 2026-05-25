-- =============================================================================
-- NOVADESK API - Create shadow_users Table
-- Version  : 1.2
-- Created  : 2026-05-15
-- Purpose  : Create the shadow_users table for storing local projections of
--            AuthHub identities, scoped to the Finance module.
--
-- Table    : af_novadesk.shadow_users
-- =============================================================================

-- Create shadow_users table
CREATE TABLE IF NOT EXISTS af_novadesk.shadow_users (
    id UUID NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    auth_user_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(150),
    last_synced_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_shadow_user_auth_id UNIQUE (auth_user_id),
    CONSTRAINT uk_shadow_user_email UNIQUE (email)
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_shadow_user_org_id ON af_novadesk.shadow_users (organization_id);

-- Add comments
COMMENT ON TABLE af_novadesk.shadow_users IS 'Local projection of AuthHub identities for the Finance module';
COMMENT ON COLUMN af_novadesk.shadow_users.auth_user_id IS 'JWT sub claim from AuthHub - canonical cross-service identity';
COMMENT ON COLUMN af_novadesk.shadow_users.organization_id IS 'Organization context from JWT organizationId claim';
COMMENT ON COLUMN af_novadesk.shadow_users.email IS 'Cached from JWT email claim';
COMMENT ON COLUMN af_novadesk.shadow_users.display_name IS 'Optional display name from JWT claims';
COMMENT ON COLUMN af_novadesk.shadow_users.last_synced_at IS 'Timestamp of last successful upsert from JWT';

