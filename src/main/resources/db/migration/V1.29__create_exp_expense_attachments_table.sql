-- =============================================================================
-- NOVADESK API - Create exp_expense_attachments Table
-- Version  : 1.29
-- Created  : 2026-05-22
-- Purpose  : Metadata for encrypted files (invoices, receipts) attached to an
--            expense transaction. Actual files live in S3 / MinIO;
--            only the storage key is persisted here. (LLR-FIN-03.4)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.exp_expense_attachments (
    id                       UUID          PRIMARY KEY DEFAULT public.gen_random_uuid(),
    expense_transaction_id   UUID          NOT NULL REFERENCES af_novadesk.exp_expense_transactions(id) ON DELETE CASCADE,
    uploaded_by_shadow_user_id UUID        NOT NULL REFERENCES af_novadesk.shadow_users(id)             ON DELETE RESTRICT,
    original_file_name       VARCHAR(255)  NOT NULL,
    file_type                VARCHAR(10)   NOT NULL,
    file_size_bytes          INTEGER       NOT NULL CHECK (file_size_bytes > 0),
    storage_key              VARCHAR(500)  NOT NULL,
    is_encrypted             BOOLEAN       NOT NULL DEFAULT TRUE,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_exp_attach_file_type
        CHECK (file_type IN ('PDF','PNG','JPG','JPEG')),

    CONSTRAINT chk_exp_attach_file_size
        CHECK (file_size_bytes <= 5242880),

    CONSTRAINT chk_exp_attach_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_exp_attach_transaction_id
    ON af_novadesk.exp_expense_attachments (expense_transaction_id);

COMMENT ON TABLE af_novadesk.exp_expense_attachments IS
    'File metadata for encrypted expense attachments (LLR-FIN-03.4). File content lives in object storage (S3/MinIO); storage_key references the object.';

COMMENT ON COLUMN af_novadesk.exp_expense_attachments.storage_key IS
    'Object store key. Convention: <orgId>/<entityId>/<transactionId>/<uuid>.<ext>. Never expose directly — use pre-signed URLs.';

COMMENT ON COLUMN af_novadesk.exp_expense_attachments.file_size_bytes IS
    'Max 5 242 880 bytes (5 MB) enforced by check constraint and service layer.';

COMMENT ON COLUMN af_novadesk.exp_expense_attachments.is_encrypted IS
    'Always TRUE for production uploads. Flag exists to support audit queries and future key-rotation tracking.';
