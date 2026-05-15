-- =============================================================================
-- NOVADESK API - Migration Strategy & Implementation Guide
-- Version  : 1.0
-- Created  : 2026-05-15
-- Purpose  : Document the Flyway migration strategy for all entities in the
--            af-novadesk-api Finance module.
-- =============================================================================

MIGRATION OVERVIEW
==================

This document outlines the Flyway migration strategy for the af-novadesk-api
Finance module. All 9 domain entities have been translated into database tables
with full referential integrity and optimized indexes for production performance.

MIGRATION FILES CREATED (in execution order)
=============================================

1. V1.1__init.sql (EXISTING)
   - Bootstrap migration: creates af_novadesk schema
   - Installs pgcrypto extension (gen_random_uuid())
   - No domain tables yet (as per original strategy)

2. V1.2__create_shadow_users_table.sql
   Table: shadow_users
   - Local projection of AuthHub identities scoped to Finance module
   - 1 index: idx_shadow_user_org_id
   - 2 unique constraints: auth_user_id, email
   - All shadow users cached from JWT claims (lazy upsert strategy)

3. V1.3__create_legal_entities_table.sql
   Table: legal_entities
   - Independent legal entities (country-specific subsidiaries)
   - 2 indexes: country, approval_status
   - 2 unique constraints: entity_name, entity_code
   - Parent aggregate for relationships to fiscal year, CoA, bank accounts, user accesses

4. V1.4__create_fiscal_year_settings_table.sql
   Table: fiscal_year_settings
   - Country-specific fiscal year configurations (1:1 with legal_entities)
   - 1 index: legal_entity_id
   - Supports multi-country fiscal calendars (US, India, Nepal, etc.)
   - Includes CHECK constraints for month/day validation (1-12, 1-31)

5. V1.5__create_chart_of_accounts_table.sql
   Table: chart_of_accounts
   - Chart of Accounts for each legal entity
   - 3 indexes: legal_entity_id, parent_account_id, composite (entity + type)
   - Self-referential hierarchy: parent_account_id → id
   - Unique constraint: (legal_entity_id, account_code)

6. V1.6__create_entity_bank_accounts_table.sql
   Table: entity_bank_accounts
   - Bank and cash account records per legal entity
   - 2 indexes: legal_entity_id, composite (entity + account_type)
   - Unique constraint: (legal_entity_id, account_type)
   - System-generated flag to distinguish template defaults from user additions

7. V1.7__create_entity_user_accesses_table.sql
   Table: entity_user_accesses
   - Access grants linking ShadowUsers to LegalEntities
   - 3 indexes: shadow_user_id, legal_entity_id, (user + last_accessed_at)
   - Unique constraint: (shadow_user_id, legal_entity_id)
   - Audit trail: last_accessed_at tracks entity context switches

8. V1.8__create_legal_entity_outbox_events_table.sql
   Table: legal_entity_outbox_events
   - Transactional Outbox for LegalEntity aggregate
   - 4 indexes: (status + created_at), aggregate_id, organization_id, (status + next_retry_at)
   - Polling query efficiently filters PENDING events
   - Exponential backoff for retry logic
   - JSON payload for extensibility

9. V1.9__create_entity_user_access_outbox_events_table.sql
   Table: entity_user_access_outbox_events
   - Transactional Outbox for EntityUserAccess aggregate
   - 5 indexes: (status + created_at), aggregate_id, org_id, affected_user, (status + next_retry_at)
   - Security-critical: access grant/revoke events
   - Denormalised affected_auth_user_id for audit queries

10. V1.10__create_shadow_user_outbox_events_table.sql
    Table: shadow_user_outbox_events
    - Transactional Outbox for ShadowUser aggregate
    - 4 indexes: (status + created_at), aggregate_id, org_id, (status + next_retry_at)
    - Identity projection sync events

ENTITY RELATIONSHIP DIAGRAM
============================

shadow_users (1) ──→ (N) entity_user_accesses (N) ←── (1) legal_entities
                                                          ↓
                                                    1 ← fiscal_year_settings
                                                    ↓
                                                    N ← chart_of_accounts (self-referential parent)
                                                    ↓
                                                    N ← entity_bank_accounts

Outbox Tables (Event Storage):
  legal_entities ──FK── legal_entity_outbox_events
  entity_user_accesses ──FK── entity_user_access_outbox_events
  shadow_users ──FK── shadow_user_outbox_events

DEPLOYMENT SEQUENCE
===================

The migrations are designed to execute in order without circular dependencies:

1. V1.1: Schema bootstrap (existing)
2. V1.2: shadow_users (no dependencies)
3. V1.3: legal_entities (no dependencies)
4. V1.4: fiscal_year_settings (→ legal_entities FK)
5. V1.5: chart_of_accounts (→ legal_entities FK + self-referential)
6. V1.6: entity_bank_accounts (→ legal_entities FK)
7. V1.7: entity_user_accesses (→ shadow_users FK, → legal_entities FK)
8. V1.8: legal_entity_outbox_events (→ legal_entities FK)
9. V1.9: entity_user_access_outbox_events (→ entity_user_accesses FK)
10. V1.10: shadow_user_outbox_events (→ shadow_users FK)

DESIGN PRINCIPLES APPLIED
==========================

1. 3NF Normalization
   - Separated concerns (fiscal year, accounts, bank details, user access)
   - Eliminated transitive dependencies
   - Each table has a single clear purpose

2. Referential Integrity
   - ALL foreign keys use CASCADE on DELETE
   - Unique constraints enforce business rules (entity codes, access grants)
   - CHECK constraints validate enum ranges (months 1-12, days 1-31)

3. Multi-Tenancy & Security
   - organization_id denormalised in outbox tables (org-level filtering)
   - affected_auth_user_id in access event table (audit trail)
   - All queries can be implicitly filtered by org scope

4. Event Sourcing (Transactional Outbox)
   - Three separate outbox tables for LegalEntity, EntityUserAccess, ShadowUser
   - Polling query indices: (status, created_at), (status, next_retry_at)
   - Idempotency keys prevent duplicate message publication
   - Payload stored as JSONB for flexibility and schema evolution

5. Performance & Observability
   - Composite indexes for common query patterns
   - Covering indexes where appropriate
   - Partial indexes on retry logic (WHERE status IN (...))
   - Detailed comments on every table and column for future maintainers

TESTING CHECKLIST
=================

After deployment, verify:

[ ] All 10 migrations executed in order (check Flyway history table)
[ ] All tables created in af_novadesk schema
[ ] All foreign keys properly created and enforced
[ ] All indexes created and queryable
[ ] sample INSERT on each table succeeds (audit columns auto-populated)
[ ] Cascade deletes work (delete legal_entity → check fiscal_year_setting, CoA, bank accounts gone)
[ ] Outbox polling query returns proper PENDING events
[ ] UUID generation works (gen_random_uuid() in public schema resolved)

FUTURE EXTENSIONS
=================

As new requirements emerge:

1. Add new entity tables in V1.11, V1.12, etc. (do not modify existing migrations)
2. If adding columns to existing tables, create V1.11__add_column_to_xyz.sql
3. Always maintain idempotent migrations (can re-run without error)
4. New outbox tables follow same pattern (status, payload, idempotency_key, retry logic)
5. Document any new enum types in application code and SQL migrations

MAINTENANCE & MONITORING
========================

1. Monitor Flyway history table for failed migrations
2. Check outbox polling for DEAD events (manual intervention needed)
3. Index statistics should be analysed after bulk entity creation
4. Audit trailing on legal_entity_outbox_events for compliance
5. Check organization_id filtering in security audit queries

=============================================================================

