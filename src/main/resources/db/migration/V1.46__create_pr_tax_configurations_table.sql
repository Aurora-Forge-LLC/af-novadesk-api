-- =============================================================================
-- NOVADESK API - Create pr_tax_configurations Table
-- Version  : 1.46
-- Created  : 2026-06-01
-- Purpose  : Per-entity tax configuration for a specific jurisdiction.
--            Tax slabs and rates stored in database so admins can update
--            via UI without code changes.
--            (LLR-PAY-04.6)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_tax_configurations (
    id                       UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id          UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)   ON DELETE RESTRICT,
    jurisdiction             VARCHAR(10)    NOT NULL,
    ssf_employee_rate        DECIMAL(5,4),
    ssf_employer_rate        DECIMAL(5,4),
    ssf_max_cap_amount       DECIMAL(19,4),
    pf_employee_rate         DECIMAL(5,4),
    pf_employer_rate         DECIMAL(5,4),
    pf_max_cap_amount        DECIMAL(19,4),
    professional_tax_amount  DECIMAL(19,4),
    professional_tax_state   VARCHAR(50),
    effective_from           DATE           NOT NULL,
    effective_to             DATE,
    is_active                BOOLEAN        NOT NULL DEFAULT TRUE,
    last_modified_by         UUID           REFERENCES af_novadesk.pr_employees(id)               ON DELETE SET NULL,
    status                   VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tc_entity_jurisdiction
        UNIQUE (legal_entity_id, jurisdiction),
    CONSTRAINT chk_tc_jurisdiction
        CHECK (jurisdiction IN ('NEPAL','INDIA','USA')),
    CONSTRAINT chk_tc_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_tc_record_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

COMMENT ON TABLE af_novadesk.pr_tax_configurations IS
    'Per-entity tax configuration (LLR-PAY-04.6). Stores SSF/PF rates and references TaxSlabs.';
