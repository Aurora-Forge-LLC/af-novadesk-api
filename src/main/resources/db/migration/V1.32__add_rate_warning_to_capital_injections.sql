-- =============================================================================
-- NOVADESK API - Add rate_warning to fa_capital_injections
-- Version  : 1.32
-- Created  : 2026-05-26
-- Purpose  : Flags capital injections whose USD conversion used a lookback rate
--            (LLR-FIN-04.4 — Historical Rate Handling with warning flag).
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.fa_capital_injections
  ADD COLUMN IF NOT EXISTS rate_warning BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN af_novadesk.fa_capital_injections.rate_warning IS
  'True when a lookback rate was used for USD conversion — flags the injection for review.';
