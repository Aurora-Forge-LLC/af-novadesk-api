-- =============================================================================
-- AF NovaDesk API — SIT Database Cleanup Script
-- 
-- Truncates ALL data from the af_novadesk SIT PostgreSQL database while
-- preserving the schema (tables, indexes, constraints, functions).
--
-- Schemas affected:
--   af_novadesk       — main application data
--   af_novadesk_outbox — transactional outbox events
--
-- ⚠️ WARNING: This destroys ALL data. There is NO recovery.
-- ⚠️ Run only against the SIT environment, never PROD.
-- =============================================================================

SET search_path TO af_novadesk, af_novadesk_outbox;

BEGIN;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Disable triggers to prevent FK checks and audit triggers from firing
-- ─────────────────────────────────────────────────────────────────────────────
SET session_replication_role = replica;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Truncate af_novadesk_outbox schema tables (order-independent, no FKs
--    between outbox tables, and outbox FKs reference af_novadesk tables)
-- ─────────────────────────────────────────────────────────────────────────────
TRUNCATE TABLE af_novadesk_outbox.ast_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.exp_expense_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.payslip_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.payroll_batch_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.leave_request_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.employee_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.capital_injection_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.exchange_rate_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.legal_entity_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.entity_user_access_outbox;
TRUNCATE TABLE af_novadesk_outbox.shadow_user_outbox_events;
TRUNCATE TABLE af_novadesk_outbox.employee_outbox_event;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Truncate af_novadesk schema tables in dependency-safe order
--    (child tables first, then parents)
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Asset Management (ast_*) ──
TRUNCATE TABLE af_novadesk.ast_custody_transfers;
TRUNCATE TABLE af_novadesk.ast_asset_returns;
TRUNCATE TABLE af_novadesk.ast_asset_assignments;
TRUNCATE TABLE af_novadesk.ast_write_offs;
TRUNCATE TABLE af_novadesk.ast_depreciation_schedules;
TRUNCATE TABLE af_novadesk.ast_asset_attachments;
TRUNCATE TABLE af_novadesk.ast_assets;

-- ── Bank Reconciliation (bnk_*) ──
TRUNCATE TABLE af_novadesk.bnk_suggested_matches;
TRUNCATE TABLE af_novadesk.bnk_vendor_mappings;
TRUNCATE TABLE af_novadesk.bnk_transactions;
TRUNCATE TABLE af_novadesk.bnk_statements;

-- ── Payroll (pr_*) ──
TRUNCATE TABLE af_novadesk.pr_leave_transactions;
TRUNCATE TABLE af_novadesk.pr_leave_balances;
TRUNCATE TABLE af_novadesk.pr_leave_requests;
TRUNCATE TABLE af_novadesk.pr_leave_policies;
TRUNCATE TABLE af_novadesk.pr_payslip_line_items;
TRUNCATE TABLE af_novadesk.pr_payslips;
TRUNCATE TABLE af_novadesk.pr_payroll_flagged_employees;
TRUNCATE TABLE af_novadesk.pr_payroll_ledger_entries;
TRUNCATE TABLE af_novadesk.pr_payroll_audit_logs;
TRUNCATE TABLE af_novadesk.pr_payroll_details;
TRUNCATE TABLE af_novadesk.pr_payroll_batches;
TRUNCATE TABLE af_novadesk.pr_tax_slabs;
TRUNCATE TABLE af_novadesk.pr_tax_configurations;

-- ── Common Employees (cm_*) ──
TRUNCATE TABLE af_novadesk.cm_employee_entity_assignments;
TRUNCATE TABLE af_novadesk.cm_employees;

-- ── Payroll Employees (pr_employees) — now dropped in V1.86, but ensure clean ──
TRUNCATE TABLE af_novadesk.pr_employees;

-- ── Expense (exp_*) ──
TRUNCATE TABLE af_novadesk.exp_expense_attachments;
TRUNCATE TABLE af_novadesk.exp_expense_transactions;
TRUNCATE TABLE af_novadesk.exp_vendors;

-- ── Finance (fa_*) ──
TRUNCATE TABLE af_novadesk.fa_ledger_entries;
TRUNCATE TABLE af_novadesk.fa_capital_injections;
TRUNCATE TABLE af_novadesk.fa_exchange_rates;
TRUNCATE TABLE af_novadesk.fa_accounts;

-- ── Entity/Legal (entity_*, legal_*) ──
TRUNCATE TABLE af_novadesk.entity_bank_accounts;
TRUNCATE TABLE af_novadesk.entity_user_accesses;
TRUNCATE TABLE af_novadesk.chart_of_accounts;
TRUNCATE TABLE af_novadesk.fiscal_year_settings;
TRUNCATE TABLE af_novadesk.legal_entities;

-- ── Identity (shadow_users) ──
TRUNCATE TABLE af_novadesk.shadow_users;

-- ── Seed Data / Template tables (populated by migrations, keep data) ──
-- Comment these out if you want to preserve seed data:
-- TRUNCATE TABLE af_novadesk.coa_templates;
-- TRUNCATE TABLE af_novadesk.bank_account_templates;
-- TRUNCATE TABLE af_novadesk.fa_account_templates;
-- TRUNCATE TABLE af_novadesk.fiscal_year_templates;

-- ── Exchange Rates (global reference data) ──
-- Comment out if you want to preserve exchange rate reference data:
-- TRUNCATE TABLE af_novadesk.fa_exchange_rates;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Re-enable triggers
-- ─────────────────────────────────────────────────────────────────────────────
SET session_replication_role = DEFAULT;

COMMIT;

-- ═════════════════════════════════════════════════════════════════════════════
-- Post-cleanup tasks:
-- ═════════════════════════════════════════════════════════════════════════════
--
-- 1. Restart the application containers so they re-establish connections:
--    docker compose -p af-novadesk-sit down && docker compose -p af-novadesk-sit up -d
--
-- 2. Verify the application health:
--    curl -s https://novadesk-api.sit.auroraforge.co/novadesk-api/actuator/health
--
-- 3. Verify the database is clean:
--    SELECT count(*) FROM af_novadesk.legal_entities;
--    SELECT count(*) FROM af_novadesk_outbox.ast_outbox_events;
-- ═════════════════════════════════════════════════════════════════════════════
