-- =============================================================================
-- NOVADESK API - Fix currency_code column type in ast_assets
-- Version  : 1.64
-- Created  : 2026-06-03
-- Purpose  : currency_code was defined as CHAR(3) (bpchar) which conflicts
--            with Hibernate's VARCHAR mapping for Java String. Converting to
--            VARCHAR(3) to match the entity mapping.
-- =============================================================================

SET search_path TO af_novadesk;

ALTER TABLE af_novadesk.ast_assets
    ALTER COLUMN currency_code TYPE VARCHAR(3);
