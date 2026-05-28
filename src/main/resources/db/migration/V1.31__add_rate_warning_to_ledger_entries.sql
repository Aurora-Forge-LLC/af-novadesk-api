-- =============================================================================
-- NOVADESK API - Add rate_warning to fa_ledger_entries
-- Version  : 1.31
-- Created  : 2026-05-26
-- Purpose  : Flags ledger entries whose USD conversion used a lookback rate
--            (LLR-FIN-04.4 — Historical Rate Handling with warning flag).
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.fa_ledger_entries
  ADD COLUMN IF NOT EXISTS rate_warning BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN af_novadesk.fa_ledger_entries.rate_warning IS
  'True when a lookback rate was used (nearest past rate within window) — flags the entry for review.';
