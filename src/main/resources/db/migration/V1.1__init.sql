-- =============================================================================
-- NOVADESK API - Initial Schema Setup
-- Version  : 1.1
-- Created  : 2026-05-11
-- Purpose  : Bootstrap migration for af-novadesk-api.
--            Creates the af_novadesk schema and installs required extensions.
--            Domain-specific tables will be added in subsequent versioned migrations.
--
-- Database : af_novadesk  (dedicated database created once by scripts/init-db.sh
--            before the first deploy; JDBC URL points here)
-- Schema   : af_novadesk  (auto-created by Flyway via flyway.schemas=af_novadesk;
--            Flyway default-schema points here so the schema history table lives here)
-- =============================================================================

-- Enable pgcrypto for gen_random_uuid() — installed into public schema.
-- No-op if already present. Requires superuser (POSTGRES_USER from infra).
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA public;

-- All objects in this script belong to the af_novadesk schema.
-- public is included so gen_random_uuid() (installed in public by pgcrypto) resolves.
SET search_path TO af_novadesk, public;

-- =============================================================================
-- SCHEMA READY
-- Domain tables (tickets, comments, attachments, etc.) will be added in
-- subsequent versioned Flyway migrations (V1.2__..., V1.3__..., etc.).
-- =============================================================================

