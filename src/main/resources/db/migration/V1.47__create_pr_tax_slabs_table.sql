-- =============================================================================
-- NOVADESK API - Create pr_tax_slabs Table
-- Version  : 1.47
-- Created  : 2026-06-01
-- Purpose  : Progressive tax rate slabs within a TaxConfiguration.
--            income_to is null for the highest (unlimited) slab.
--            Slabs are ordered by slab_order.
--            (LLR-PAY-04.3–04.4)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_tax_slabs (
    id                     UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    tax_configuration_id   UUID           NOT NULL REFERENCES af_novadesk.pr_tax_configurations(id) ON DELETE CASCADE,
    slab_order             INTEGER        NOT NULL CHECK (slab_order > 0),
    income_from            DECIMAL(19,4)  NOT NULL CHECK (income_from >= 0),
    income_to              DECIMAL(19,4),
    tax_rate               DECIMAL(5,4)   NOT NULL CHECK (tax_rate >= 0),
    is_annual              BOOLEAN        NOT NULL DEFAULT TRUE,
    description            VARCHAR(200),
    status                 VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ts_config_order
        UNIQUE (tax_configuration_id, slab_order),
    CONSTRAINT chk_ts_income_range
        CHECK (income_to IS NULL OR income_to >= income_from),
    CONSTRAINT chk_ts_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

COMMENT ON TABLE af_novadesk.pr_tax_slabs IS
    'Progressive tax rate slabs (LLR-PAY-04.3–04.4). income_to is null for the highest slab.';
