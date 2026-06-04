-- =============================================================================
-- NOVADESK API - Create ast_asset_attachments Table
-- Version  : 1.68
-- Created  : 2026-06-04
-- Purpose  : Metadata for files (manuals, invoices, photos, certificates)
--            attached to an asset. Actual files live in MinIO/S3;
--            only the storage key is persisted here. (LLR-AST-01)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_asset_attachments (
    id                       UUID          PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id                 UUID          NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE CASCADE,
    uploaded_by_shadow_user_id UUID        NOT NULL REFERENCES af_novadesk.shadow_users(id) ON DELETE RESTRICT,
    organization_id          UUID          NOT NULL,

    original_file_name       VARCHAR(255)  NOT NULL,
    file_type                VARCHAR(10)   NOT NULL,
    file_size_bytes          INTEGER       NOT NULL,
    storage_key              VARCHAR(500)  NOT NULL,

    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ast_attach_file_type
        CHECK (file_type IN ('PDF','PNG','JPG','JPEG','DOCX','XLSX')),

    CONSTRAINT chk_ast_attach_file_size
        CHECK (file_size_bytes > 0 AND file_size_bytes <= 10485760),  -- max 10 MB

    CONSTRAINT chk_ast_attach_status
        CHECK (status IN ('ACTIVE','DELETED'))
);

CREATE INDEX idx_ast_attach_asset_id
    ON af_novadesk.ast_asset_attachments (asset_id);

CREATE INDEX idx_ast_attach_org
    ON af_novadesk.ast_asset_attachments (organization_id);

COMMENT ON TABLE af_novadesk.ast_asset_attachments IS
    'File metadata for asset attachments (manuals, purchase invoices, warranty docs, photos). File content lives in MinIO; storage_key references the object.';

COMMENT ON COLUMN af_novadesk.ast_asset_attachments.storage_key IS
    'Object store key. Convention: assets/attachments/<assetId>/<uuid>.<ext>. Never expose directly — use pre-signed URLs.';

COMMENT ON COLUMN af_novadesk.ast_asset_attachments.file_size_bytes IS
    'Max 10 485 760 bytes (10 MB) enforced by check constraint and service layer.';
