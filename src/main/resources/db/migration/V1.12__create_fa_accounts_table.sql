-- =============================================================================
-- NOVADESK API - Create fa_accounts Table
-- Version  : 1.12
-- Created  : 2026-05-18
-- Purpose  : Funding accounts (equity, loan, inter-entity, cash/bank)
--            per legal entity for capital-injection workflows.
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.fa_accounts (
    id              UUID          PRIMARY KEY DEFAULT public.gen_random_uuid(),
    legal_entity_id UUID          NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    account_code    VARCHAR(30)   NOT NULL,
    account_name    VARCHAR(150)  NOT NULL,
    account_role    VARCHAR(50)   NOT NULL,
    account_type    VARCHAR(30)   NOT NULL,
    currency_code   CHAR(3)       NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fa_accounts_entity_code UNIQUE (legal_entity_id, account_code),
    CONSTRAINT chk_fa_accounts_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_accounts_entity_role
    ON af_novadesk.fa_accounts (legal_entity_id, account_role);

COMMENT ON TABLE af_novadesk.fa_accounts IS
    'Funding accounts used by the capital injection workflow.';

