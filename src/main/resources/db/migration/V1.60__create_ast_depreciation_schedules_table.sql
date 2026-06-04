-- =============================================================================
-- NOVADESK API - Create ast_depreciation_schedules Table
-- Version  : 1.60
-- Created  : 2026-06-02
-- Purpose  : Stores the year-by-year depreciation schedule for each asset.
--            One row per asset per fiscal year. The scheduled job posts
--            depreciation at year-end and marks is_posted = true.
--            journal_entry_id is a loose reference to fa_ledger_entries
--            (not a hard FK — the accounting module owns that record).
--            (LLR-AST-04)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.ast_depreciation_schedules (
    id                       UUID            PRIMARY KEY DEFAULT public.gen_random_uuid(),
    asset_id                 UUID            NOT NULL REFERENCES af_novadesk.ast_assets(id) ON DELETE RESTRICT,
    organization_id          UUID            NOT NULL,

    -- Schedule line
    fiscal_year              INT             NOT NULL,
    annual_depreciation      DECIMAL(19,4)   NOT NULL,
    accumulated_depreciation DECIMAL(19,4)   NOT NULL,
    net_book_value           DECIMAL(19,4)   NOT NULL,

    -- Posting state
    is_posted                BOOLEAN         NOT NULL DEFAULT FALSE,
    journal_entry_id         UUID,
    posted_at                TIMESTAMPTZ,

    -- Audit
    created_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_ast_depr_asset_year  UNIQUE (asset_id, fiscal_year),
    CONSTRAINT ck_ast_depr_year        CHECK  (fiscal_year > 2000),
    CONSTRAINT ck_ast_depr_amount      CHECK  (annual_depreciation >= 0),
    CONSTRAINT ck_ast_depr_nbv         CHECK  (net_book_value >= 0)
);

CREATE INDEX idx_ast_depr_asset   ON af_novadesk.ast_depreciation_schedules (asset_id);
CREATE INDEX idx_ast_depr_org     ON af_novadesk.ast_depreciation_schedules (organization_id, fiscal_year);
CREATE INDEX idx_ast_depr_pending ON af_novadesk.ast_depreciation_schedules (organization_id, fiscal_year, is_posted)
    WHERE is_posted = FALSE;

COMMENT ON TABLE  af_novadesk.ast_depreciation_schedules                   IS 'Year-by-year depreciation schedule (LLR-AST-04).';
COMMENT ON COLUMN af_novadesk.ast_depreciation_schedules.journal_entry_id  IS 'Loose reference to fa_ledger_entries.id — not enforced as FK (accounting module owns it).';
COMMENT ON COLUMN af_novadesk.ast_depreciation_schedules.is_posted         IS 'Set to TRUE by the fiscal-year-end scheduler after posting the journal entry.';
