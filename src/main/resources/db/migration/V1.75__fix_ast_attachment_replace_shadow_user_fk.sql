-- =============================================================================
-- NOVADESK API - Replace shadow_user FK on ast_asset_attachments with loose UUID
-- Version  : 1.75
-- Created  : 2026-06-05
-- Purpose  : Phase 6 asset module cleanup.
--            ast_asset_attachments.uploaded_by_shadow_user_id is a hard FK to
--            shadow_users (identity module). Replace it with a loose UUID column
--            uploaded_by_auth_user_id so the asset module has zero hard
--            dependencies on the identity module's tables.
-- =============================================================================

SET search_path TO af_novadesk;

-- 1. Add loose UUID column (auth_user_id from shadow_users)
ALTER TABLE af_novadesk.ast_asset_attachments
    ADD COLUMN IF NOT EXISTS uploaded_by_auth_user_id UUID;

-- 2. Populate from shadow_users.auth_user_id
UPDATE af_novadesk.ast_asset_attachments a
SET uploaded_by_auth_user_id = su.auth_user_id
FROM af_novadesk.shadow_users su
WHERE su.id = a.uploaded_by_shadow_user_id;

-- 3. Set NOT NULL (all rows should now be populated)
ALTER TABLE af_novadesk.ast_asset_attachments
    ALTER COLUMN uploaded_by_auth_user_id SET NOT NULL;

-- 4. Drop the FK constraint and the old column
ALTER TABLE af_novadesk.ast_asset_attachments
    DROP CONSTRAINT IF EXISTS fk_ast_attach_uploaded_by;

ALTER TABLE af_novadesk.ast_asset_attachments
    DROP COLUMN IF EXISTS uploaded_by_shadow_user_id;

COMMENT ON COLUMN af_novadesk.ast_asset_attachments.uploaded_by_auth_user_id IS
    'Loose reference to shadow_users.auth_user_id — no FK to keep asset module decoupled from identity module.';
