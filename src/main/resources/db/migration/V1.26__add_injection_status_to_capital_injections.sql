-- =============================================================================
-- NOVADESK API - Add injection_status column to fa_capital_injections
-- Version  : 1.26
-- Created  : 2026-05-21
-- Purpose  : Track lifecycle status of capital injection records (LLR-FIN-02).
--            Values: POSTED, PENDING_REVIEW, FAILED, VOID (default: POSTED).
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.fa_capital_injections
    ADD COLUMN IF NOT EXISTS injection_status VARCHAR(20) NOT NULL DEFAULT 'POSTED';

COMMENT ON COLUMN af_novadesk.fa_capital_injections.injection_status IS
    'Lifecycle status: POSTED (default), PENDING_REVIEW, FAILED, VOID (LLR-FIN-02).';
