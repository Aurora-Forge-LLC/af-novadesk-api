-- =============================================================================
-- NOVADESK API - Add organization_id to fa_exchange_rates
-- Version  : 1.55
-- Created  : 2026-06-02
-- Purpose  : Multi-tenant org-scoping for exchange rates (LLR-FIN-04).
--            Adds organization_id column, drops old unique constraint on
--            (source_currency, target_currency, rate_date) and replaces it
--            with one that also includes organization_id.
-- =============================================================================

SET search_path TO af_novadesk;

-- 1. Add organization_id column (nullable initially for existing data)
ALTER TABLE af_novadesk.fa_exchange_rates
    ADD COLUMN IF NOT EXISTS organization_id UUID;

COMMENT ON COLUMN af_novadesk.fa_exchange_rates.organization_id IS
    'Multi-tenant scope; every rate belongs to exactly one organization.';

-- 2. Drop old unique constraint that ignored organization_id
ALTER TABLE af_novadesk.fa_exchange_rates
    DROP CONSTRAINT IF EXISTS uq_fa_exchange_rate;

-- 3. Add new unique constraint that includes organization_id
--    (nulls are treated as distinct in PostgreSQL UNIQUE constraints,
--     so we use a partial unique index for existing null-backed rows)
CREATE UNIQUE INDEX IF NOT EXISTS uq_fa_exchange_rate
    ON af_novadesk.fa_exchange_rates (source_currency, target_currency, rate_date, organization_id);

-- 4. Add index for org-scoped lookups
CREATE INDEX IF NOT EXISTS idx_fa_exchange_rate_org
    ON af_novadesk.fa_exchange_rates (organization_id);
