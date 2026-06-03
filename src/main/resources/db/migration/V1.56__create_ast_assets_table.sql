-- =============================================================================
-- NOVADESK API - Create ast_assets Table
-- Version  : 1.56
-- Created  : 2026-06-02
-- Purpose  : Core asset registry. Every physical asset owned by a legal entity
--            is registered here with a unique serial number, purchase details,
--            depreciation configuration, and lifecycle status.
--            (LLR-AST-01)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_assets (
    id                       UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id          UUID            NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    organization_id          UUID            NOT NULL,

    -- Classification
    category                 VARCHAR(20)     NOT NULL,
    asset_type               VARCHAR(150)    NOT NULL,
    manufacturer             VARCHAR(150),
    model_number             VARCHAR(150),
    serial_number            VARCHAR(100)    NOT NULL,

    -- Purchase
    purchase_date            DATE            NOT NULL,
    purchase_cost            DECIMAL(19,4)   NOT NULL,
    currency_code            CHAR(3)         NOT NULL,
    vendor                   VARCHAR(200),

    -- Warranty
    warranty_expiry_date     DATE,

    -- Depreciation
    depreciation_method      VARCHAR(30)     NOT NULL,
    useful_life_years        INT             NOT NULL,
    net_book_value           DECIMAL(19,4)   NOT NULL,
    accumulated_depreciation DECIMAL(19,4)   NOT NULL DEFAULT 0,

    -- Location & Status
    current_location         VARCHAR(300),
    status                   VARCHAR(30)     NOT NULL DEFAULT 'AVAILABLE',

    -- Supporting
    notes                    VARCHAR(1000),
    photo_url                VARCHAR(500),
    qr_code_url              VARCHAR(500),

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ast_serial_number  UNIQUE (organization_id, serial_number),
    CONSTRAINT ck_ast_category       CHECK  (category IN ('LAPTOP','DESKTOP','MONITOR','PHONE','TABLET','SERVER','FURNITURE','OTHER')),
    CONSTRAINT ck_ast_status         CHECK  (status IN ('AVAILABLE','ASSIGNED','RETURNED','LOST','DISPOSED','FULLY_DEPRECATED')),
    CONSTRAINT ck_ast_depreciation   CHECK  (depreciation_method IN ('STRAIGHT_LINE','DECLINING_BALANCE')),
    CONSTRAINT ck_ast_useful_life    CHECK  (useful_life_years > 0),
    CONSTRAINT ck_ast_purchase_cost  CHECK  (purchase_cost > 0),
    CONSTRAINT ck_ast_net_book_value CHECK  (net_book_value >= 0)
);

CREATE INDEX idx_ast_assets_entity   ON af_novadesk.ast_assets (legal_entity_id);
CREATE INDEX idx_ast_assets_org      ON af_novadesk.ast_assets (organization_id);
CREATE INDEX idx_ast_assets_status   ON af_novadesk.ast_assets (organization_id, status);
CREATE INDEX idx_ast_assets_category ON af_novadesk.ast_assets (organization_id, category);
CREATE INDEX idx_ast_assets_serial   ON af_novadesk.ast_assets (serial_number);

COMMENT ON TABLE  af_novadesk.ast_assets                      IS 'Core asset registry (LLR-AST-01).';
COMMENT ON COLUMN af_novadesk.ast_assets.serial_number        IS 'Case-insensitive unique identifier per organisation.';
COMMENT ON COLUMN af_novadesk.ast_assets.net_book_value       IS 'purchase_cost - accumulated_depreciation. Updated annually by the depreciation scheduler.';
COMMENT ON COLUMN af_novadesk.ast_assets.qr_code_url          IS 'MinIO URL of the generated QR-code label image.';
