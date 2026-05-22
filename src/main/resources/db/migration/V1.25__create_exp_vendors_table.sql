-- =============================================================================
-- NOVADESK API - Create exp_vendors Table
-- Version  : 1.25
-- Created  : 2026-05-22
-- Purpose  : Vendor (payee) registry scoped to an organization.
--            Supports autocomplete on the expense entry form (LLR-FIN-03.3).
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.exp_vendors (
    id                  UUID          PRIMARY KEY DEFAULT public.gen_random_uuid(),
    organization_id     UUID          NOT NULL,
    vendor_name         VARCHAR(100)  NOT NULL,
    vendor_type         VARCHAR(30)   NOT NULL,
    tax_id              VARCHAR(50),
    default_account_id  UUID          REFERENCES af_novadesk.fa_accounts(id) ON DELETE SET NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_exp_vendor_name_org
        UNIQUE (vendor_name, organization_id),

    CONSTRAINT chk_exp_vendor_type
        CHECK (vendor_type IN ('SERVICE_PROVIDER','LANDLORD','UTILITY','SUPPLIER','CONTRACTOR','GOVERNMENT','OTHER')),

    CONSTRAINT chk_exp_vendor_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_exp_vendor_org_id
    ON af_novadesk.exp_vendors (organization_id);

COMMENT ON TABLE af_novadesk.exp_vendors IS
    'Vendor (payee) registry. Scoped to organization_id. Supports autocomplete and default account pre-fill on the expense form (LLR-FIN-03.3).';

COMMENT ON COLUMN af_novadesk.exp_vendors.organization_id IS
    'Organization scope — not a FK, owned by AuthHub.';

COMMENT ON COLUMN af_novadesk.exp_vendors.default_account_id IS
    'Optional FK to fa_accounts. When set, the expense form auto-fills the destination account with this value.';

COMMENT ON COLUMN af_novadesk.exp_vendors.vendor_type IS
    'Business category: SERVICE_PROVIDER, LANDLORD, UTILITY, SUPPLIER, CONTRACTOR, GOVERNMENT, OTHER.';
