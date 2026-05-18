-- =============================================================================
-- NOVADESK API - Finance Funding (LLR-FIN-02)
-- Version  : 1.12
-- Created  : 2026-05-18
-- Purpose  : Capital injection, exchange rates, and double-entry ledger tables.
--            Migrated to finance.funding package; entities now reference
--            legal_entities via proper FK relationships.
-- =============================================================================

SET search_path TO af_novadesk, public;

-- ---------------------------------------------------------------------------
-- fa_accounts
--   Funding accounts (equity, loan, inter-entity, cash/bank) per legal entity.
--   Linked to legal_entities for normalised entity resolution.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fa_accounts (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id UUID         NOT NULL
                                 REFERENCES legal_entities(id) ON DELETE RESTRICT,
    account_code    VARCHAR(30)  NOT NULL,
    account_name    VARCHAR(150) NOT NULL,
    account_role    VARCHAR(50)  NOT NULL,
    account_type    VARCHAR(30)  NOT NULL,
    currency_code   CHAR(3)      NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fa_accounts_entity_code UNIQUE (legal_entity_id, account_code),
    CONSTRAINT chk_fa_accounts_status     CHECK  (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_accounts_entity_role
    ON fa_accounts (legal_entity_id, account_role);

-- ---------------------------------------------------------------------------
-- fa_exchange_rates
--   Daily rate snapshot for each currency pair.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fa_exchange_rates (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_currency CHAR(3)      NOT NULL,
    target_currency CHAR(3)      NOT NULL,
    rate_date       DATE         NOT NULL,
    exchange_rate   DECIMAL(19,6) NOT NULL CHECK (exchange_rate > 0),
    rate_source     VARCHAR(30)  NOT NULL,
    created_by      VARCHAR(100),
    approved_by     VARCHAR(100),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fa_exchange_rate UNIQUE (source_currency, target_currency, rate_date),
    CONSTRAINT chk_fa_exchange_rates_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_exchange_rate_lookup
    ON fa_exchange_rates (source_currency, target_currency, rate_date DESC);

-- ---------------------------------------------------------------------------
-- fa_capital_injections
--   Header record for each capital injection event.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fa_capital_injections (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id            UUID,
    target_legal_entity_id UUID         NOT NULL
                                         REFERENCES legal_entities(id) ON DELETE RESTRICT,
    source_legal_entity_id UUID         REFERENCES legal_entities(id) ON DELETE RESTRICT,
    funding_source         VARCHAR(30)  NOT NULL,
    funding_date           DATE         NOT NULL,
    amount_local           DECIMAL(19,4) NOT NULL CHECK (amount_local >= 0.01),
    currency_local         CHAR(3)      NOT NULL,
    amount_usd             DECIMAL(19,4) NOT NULL CHECK (amount_usd >= 0.01),
    exchange_rate_used     DECIMAL(19,6) NOT NULL CHECK (exchange_rate_used > 0),
    rate_date_used         DATE         NOT NULL,
    rate_source            VARCHAR(30)  NOT NULL,
    source_account_id      UUID         NOT NULL
                                         REFERENCES fa_accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID         NOT NULL
                                         REFERENCES fa_accounts(id) ON DELETE RESTRICT,
    reference_number       VARCHAR(50),
    notes                  VARCHAR(500),
    created_by             VARCHAR(100),
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fa_capital_injections_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_capital_injections_entity_date
    ON fa_capital_injections (target_legal_entity_id, funding_date DESC);

CREATE INDEX IF NOT EXISTS idx_fa_capital_injections_transfer
    ON fa_capital_injections (transfer_id)
    WHERE transfer_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- fa_ledger_entries
--   Individual double-entry lines; append-only after creation.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fa_ledger_entries (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_id       UUID         NOT NULL,
    transfer_id      UUID,
    legal_entity_id  UUID         NOT NULL
                                   REFERENCES legal_entities(id) ON DELETE RESTRICT,
    account_id       UUID         NOT NULL
                                   REFERENCES fa_accounts(id) ON DELETE RESTRICT,
    entry_side       VARCHAR(10)  NOT NULL   CHECK (entry_side IN ('DEBIT','CREDIT')),
    amount_local     DECIMAL(19,4) NOT NULL  CHECK (amount_local >= 0.01),
    currency_local   CHAR(3)      NOT NULL,
    amount_usd       DECIMAL(19,4) NOT NULL  CHECK (amount_usd >= 0.01),
    exchange_rate_used DECIMAL(19,6) NOT NULL CHECK (exchange_rate_used > 0),
    rate_date_used   DATE         NOT NULL,
    description      VARCHAR(500),
    reference_type   VARCHAR(50)  NOT NULL,
    reference_id     UUID         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fa_ledger_entries_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_journal
    ON fa_ledger_entries (journal_id);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_entity_date
    ON fa_ledger_entries (legal_entity_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fa_ledger_entries_transfer
    ON fa_ledger_entries (transfer_id)
    WHERE transfer_id IS NOT NULL;

