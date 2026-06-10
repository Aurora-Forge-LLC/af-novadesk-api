# Bank Reconciliation — Database Schema & ER Diagram

> **Schema:** `af_novadesk` (same as all Finance/Payroll/Asset tables)  
> **New Table Prefix:** `bnk_` (bank reconciliation)  
> **Migration Range:** `V1.69` – `V1.73`  
> **Status:** Planning — No code changes yet

---

## 1. Entity Relationship Diagram (Full View)

```mermaid
erDiagram
    %% ═══════════════════════════════════════════════════════════════
    %% EXISTING TABLES (shown for context)
    %% ═══════════════════════════════════════════════════════════════

    legal_entities {
        UUID        id              PK
        VARCHAR     entity_name     UK
        VARCHAR     entity_code     UK
        UUID        organization_id
        VARCHAR     country
        VARCHAR     base_currency
        VARCHAR     tax_id
        DATE        incorporation_date
        VARCHAR     approval_status
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    entity_bank_accounts {
        UUID        id                PK
        UUID        legal_entity_id   FK
        VARCHAR     account_type
        VARCHAR     account_label
        VARCHAR     bank_name
        VARCHAR     account_number
        VARCHAR     iban
        VARCHAR     swift_code
        BOOLEAN     is_system_generated
        VARCHAR     status
        TIMESTAMP   created_at
        TIMESTAMP   updated_at
    }

    fa_accounts {
        UUID        id              PK
        UUID        legal_entity_id FK
        VARCHAR     account_code
        VARCHAR     account_name
        VARCHAR     account_role
        VARCHAR     account_type
        CHAR        currency_code
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    chart_of_accounts {
        UUID        id                PK
        UUID        legal_entity_id   FK
        UUID        parent_account_id FK_SELF
        VARCHAR     account_code
        VARCHAR     account_name
        VARCHAR     account_type
        VARCHAR     description
        BOOLEAN     is_postable
        BOOLEAN     is_system_generated
        VARCHAR     status
        TIMESTAMP   created_at
        TIMESTAMP   updated_at
    }

    exp_vendors {
        UUID        id                PK
        UUID        organization_id
        VARCHAR     vendor_name       UK_ORG
        VARCHAR     vendor_type
        VARCHAR     tax_id
        UUID        default_account_id FK
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    exp_expense_transactions {
        UUID        id                         PK
        UUID        legal_entity_id            FK
        UUID        vendor_id                  FK
        UUID        created_by_shadow_user_id  FK
        DATE        expense_date
        DECIMAL     amount
        CHAR        currency_code
        VARCHAR     payment_method
        UUID        source_account_id          FK
        UUID        chart_of_account_id        FK
        VARCHAR     invoice_receipt_number
        VARCHAR     description
        VARCHAR     transaction_status
        DECIMAL     manual_exchange_rate
        VARCHAR     manual_rate_justification
        VARCHAR     manual_rate_approved_by
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    fa_ledger_entries {
        UUID        id                 PK
        UUID        journal_id
        UUID        transfer_id
        UUID        legal_entity_id    FK
        UUID        account_id         FK_NULL
        UUID        chart_of_account_id FK_NULL
        VARCHAR     entry_side
        DECIMAL     amount_local
        CHAR        currency_local
        DECIMAL     amount_usd
        DECIMAL     exchange_rate_used
        DATE        rate_date_used
        VARCHAR     description
        VARCHAR     reference_type
        UUID        reference_id
        BOOLEAN     rate_warning
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    shadow_users {
        UUID        id              PK
        UUID        auth_user_id    UK
        UUID        organization_id
        VARCHAR     email           UK
        VARCHAR     display_name
        TIMESTAMP   last_synced_at
        TIMESTAMP   created_at
        TIMESTAMP   updated_at
        VARCHAR     status
    }

    %% ═══════════════════════════════════════════════════════════════
    %% NEW TABLES — Bank Reconciliation
    %% ═══════════════════════════════════════════════════════════════

    bnk_statements {
        UUID        id                         PK
        UUID        legal_entity_id            FK "→ legal_entities"
        UUID        bank_account_id            FK "→ entity_bank_accounts"
        UUID        uploaded_by_shadow_user_id FK "→ shadow_users"
        VARCHAR     original_filename
        VARCHAR     storage_key               "MinIO object key"
        VARCHAR     file_type                 "CSV, XLSX, PDF"
        INTEGER     file_size_bytes
        BOOLEAN     is_encrypted
        DATE        period_start
        DATE        period_end
        INTEGER     transaction_count
        VARCHAR     notes
        VARCHAR     statement_status          "UPLOADED | PARSED | SUPERSEDED | FAILED"
        VARCHAR     status                    "ACTIVE | INACTIVE"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    bnk_transactions {
        UUID        id                    PK
        UUID        statement_id          FK "→ bnk_statements (CASCADE)"
        UUID        legal_entity_id       FK "→ legal_entities"
        UUID        bank_account_id       FK "→ entity_bank_accounts"
        DATE        transaction_date
        VARCHAR     description
        DECIMAL     amount                "positive=credit, negative=debit"
        DECIMAL     balance               "running balance"
        VARCHAR     reconciliation_status "UNMATCHED|SUGGESTED|MATCHED|IGNORED"
        UUID        matched_ledger_entry_id "→ fa_ledger_entries (nullable)"
        INTEGER     matching_score        "0–100"
        VARCHAR     matching_method       "AUTO_MATCHED|USER_CONFIRMED|MANUAL"
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    bnk_vendor_mappings {
        UUID        id                      PK
        UUID        legal_entity_id         FK "→ legal_entities (CASCADE)"
        VARCHAR     bank_description_pattern "SQL LIKE pattern e.g. AMZN%"
        UUID        vendor_id               FK "→ exp_vendors (CASCADE)"
        VARCHAR     confidence_level        "USER_CONFIRMED|AUTO_LEARNED"
        INTEGER     match_count
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    bnk_suggested_matches {
        UUID        id                       PK
        UUID        bank_transaction_id      FK "→ bnk_transactions (CASCADE)"
        UUID        expense_transaction_id   FK "→ exp_expense_transactions (CASCADE)"
        INTEGER     matching_score           "60–79 range"
        JSONB       score_breakdown          "{amount,date,description}"
        VARCHAR     suggested_by             "SYSTEM|USER"
        VARCHAR     suggestion_status        "PENDING|ACCEPTED|REJECTED"
        UUID        resolved_by_shadow_user_id FK "→ shadow_users (nullable)"
        TIMESTAMPTZ resolved_at
        VARCHAR     status
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    %% ═══════════════════════════════════════════════════════════════
    %% RELATIONSHIPS — Existing
    %% ═══════════════════════════════════════════════════════════════

    legal_entities ||--o{ entity_bank_accounts : "owns"
    legal_entities ||--o{ fa_accounts : "owns"
    legal_entities ||--o{ chart_of_accounts : "owns"
    legal_entities ||--o{ exp_expense_transactions : "scoped to"
    legal_entities ||--o{ fa_ledger_entries : "scoped to"

    entity_bank_accounts }o--|| legal_entities : "belongs to"
    fa_accounts }o--|| legal_entities : "belongs to"
    chart_of_accounts }o--|| legal_entities : "belongs to"

    exp_vendors }o--o| fa_accounts : "default_account"
    exp_expense_transactions }o--|| exp_vendors : "vendor"
    exp_expense_transactions }o--|| shadow_users : "created_by"
    exp_expense_transactions }o--|| fa_accounts : "source_account"
    exp_expense_transactions }o--|| chart_of_accounts : "expense_category"

    fa_ledger_entries }o--|| fa_accounts : "CREDIT side (nullable)"
    fa_ledger_entries }o--|| chart_of_accounts : "DEBIT side (nullable)"

    %% ═══════════════════════════════════════════════════════════════
    %% RELATIONSHIPS — New
    %% ═══════════════════════════════════════════════════════════════

    bnk_statements }o--|| legal_entities : "entity"
    bnk_statements }o--|| entity_bank_accounts : "bank_account"
    bnk_statements }o--|| shadow_users : "uploaded_by"
    bnk_statements ||--o{ bnk_transactions : "contains (CASCADE)"

    bnk_transactions }o--|| legal_entities : "entity"
    bnk_transactions }o--|| entity_bank_accounts : "bank_account"
    bnk_transactions }o--o| fa_ledger_entries : "matched_ledger_entry (nullable)"

    bnk_vendor_mappings }o--|| legal_entities : "entity (CASCADE)"
    bnk_vendor_mappings }o--|| exp_vendors : "vendor (CASCADE)"

    bnk_suggested_matches }o--|| bnk_transactions : "bank_txn (CASCADE)"
    bnk_suggested_matches }o--|| exp_expense_transactions : "expense_txn (CASCADE)"
    bnk_suggested_matches }o--o| shadow_users : "resolved_by (nullable)"
```

---

## 2. New Tables — Detailed Column Specifications

### 2.1 `bnk_statements` — Uploaded Bank Statements

| # | Column | Type | Null | Default | Constraint | Description |
|---|--------|------|------|---------|------------|-------------|
| 1 | `id` | `UUID` | ❌ | `gen_random_uuid()` | **PK** | Primary key |
| 2 | `legal_entity_id` | `UUID` | ❌ | — | **FK** → `legal_entities(id)` ON DELETE RESTRICT | The entity this statement belongs to |
| 3 | `bank_account_id` | `UUID` | ❌ | — | **FK** → `entity_bank_accounts(id)` ON DELETE RESTRICT | The bank account the statement is for |
| 4 | `uploaded_by_shadow_user_id` | `UUID` | ❌ | — | **FK** → `shadow_users(id)` ON DELETE RESTRICT | Finance operator who uploaded the file |
| 5 | `original_filename` | `VARCHAR(255)` | ❌ | — | — | Original filename as uploaded (e.g. `"may-2026-statement.csv"`) |
| 6 | `storage_key` | `VARCHAR(500)` | ❌ | — | — | MinIO object key: `{orgId}/bank-statements/{entityId}/{uuid}.{ext}` |
| 7 | `file_type` | `VARCHAR(10)` | ❌ | — | `CHECK (IN ('CSV','XLSX','PDF'))` | Parsed file extension |
| 8 | `file_size_bytes` | `INTEGER` | ❌ | — | `CHECK (> 0)` | Size in bytes |
| 9 | `is_encrypted` | `BOOLEAN` | ❌ | `true` | — | Always true for production; flag for audit queries |
| 10 | `period_start` | `DATE` | ❌ | — | — | Statement period start date |
| 11 | `period_end` | `DATE` | ❌ | — | — | Statement period end date |
| 12 | `transaction_count` | `INTEGER` | ❌ | `0` | — | Number of transactions extracted; set after parsing |
| 13 | `notes` | `VARCHAR(500)` | ✅ | — | — | Optional free-text notes from uploader |
| 14 | `statement_status` | `VARCHAR(20)` | ❌ | `'UPLOADED'` | `CHECK (IN ('UPLOADED','PARSED','SUPERSEDED','FAILED'))` | Lifecycle state |
| 15 | `status` | `VARCHAR(20)` | ❌ | `'ACTIVE'` | `CHECK (IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))` | Soft-delete |
| 16 | `created_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Creation timestamp |
| 17 | `updated_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Last modified timestamp |

**Indexes:**

| Index Name | Columns | Purpose |
|-----------|---------|---------|
| `idx_bnk_statements_entity` | `legal_entity_id` | List statements by entity |
| `idx_bnk_statements_bank_account` | `bank_account_id` | List statements by bank account |
| `idx_bnk_statements_period` | `bank_account_id, period_start, period_end` | **Duplicate detection** — fastest lookup |
| `idx_bnk_statements_uploaded_by` | `uploaded_by_shadow_user_id` | Audit: who uploaded what |

**Unique Constraint:**

| Constraint | Columns | Purpose |
|-----------|---------|---------|
| *(composite index)* | `bank_account_id, period_start, period_end` | Duplicate detection query uses the `idx_bnk_statements_period` index. A formal UNIQUE constraint is **not** added because: (a) SUPERSEDED statements coexist with replacements; (b) the service layer handles duplicate logic with user choice (Replace vs Cancel) |

**Lifecycle State Machine:**
```
                         ┌──────────┐
                         │ UPLOADED │  ← File stored in MinIO, not yet parsed
                         └────┬─────┘
                              │ parseStatement()
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              ┌────────┐           ┌───────┐
              │ PARSED │           │ FAILED│  ← Parse error (malformed file)
              └───┬────┘           └───────┘
                  │
                  │ replaceStatement()
                  ▼
            ┌────────────┐
            │ SUPERSEDED │  ← Replaced by newer upload
            └────────────┘
```

---

### 2.2 `bnk_transactions` — Extracted Bank Transactions

| # | Column | Type | Null | Default | Constraint | Description |
|---|--------|------|------|---------|------------|-------------|
| 1 | `id` | `UUID` | ❌ | `gen_random_uuid()` | **PK** | Primary key |
| 2 | `statement_id` | `UUID` | ❌ | — | **FK** → `bnk_statements(id)` ON DELETE CASCADE | Parent statement |
| 3 | `legal_entity_id` | `UUID` | ❌ | — | **FK** → `legal_entities(id)` ON DELETE RESTRICT | Denormalized for query performance |
| 4 | `bank_account_id` | `UUID` | ❌ | — | **FK** → `entity_bank_accounts(id)` ON DELETE RESTRICT | Denormalized for query performance |
| 5 | `transaction_date` | `DATE` | ❌ | — | — | Date from bank statement row |
| 6 | `description` | `VARCHAR(500)` | ❌ | — | — | Narration/description from statement |
| 7 | `amount` | `DECIMAL(19,4)` | ❌ | — | — | **Positive = credit (money in), Negative = debit (money out)** |
| 8 | `balance` | `DECIMAL(19,4)` | ✅ | — | — | Running balance (nullable — not all banks provide this) |
| 9 | `reconciliation_status` | `VARCHAR(30)` | ❌ | `'UNMATCHED'` | `CHECK (IN ('UNMATCHED','SUGGESTED','MATCHED','IGNORED'))` | Current matching state |
| 10 | `matched_ledger_entry_id` | `UUID` | ✅ | — | — | FK to `fa_ledger_entries(id)` — set after matching |
| 11 | `matching_score` | `INTEGER` | ✅ | — | — | Score 0–100 from matching algorithm |
| 12 | `matching_method` | `VARCHAR(30)` | ✅ | — | `CHECK (IN ('AUTO_MATCHED','USER_CONFIRMED','MANUAL'))` | How the match was established |
| 13 | `status` | `VARCHAR(20)` | ❌ | `'ACTIVE'` | `CHECK (IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))` | Soft-delete |
| 14 | `created_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Creation timestamp |
| 15 | `updated_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Last modified timestamp |

**Indexes:**

| Index Name | Columns | Purpose |
|-----------|---------|---------|
| `idx_bnk_txn_statement` | `statement_id` | Get all transactions for a statement |
| `idx_bnk_txn_entity` | `legal_entity_id` | Filter transactions by entity |
| `idx_bnk_txn_status` | `reconciliation_status` | **Unmatched queue** — WHERE reconciliation_status = 'UNMATCHED' |
| `idx_bnk_txn_date` | `transaction_date` | Date-range filters |
| `idx_bnk_txn_amount` | `amount` | Amount-range filters |
| `idx_bnk_txn_entity_status_date` | `legal_entity_id, reconciliation_status, transaction_date` | **Primary workload query** for unmatched transaction list |

**Reconciliation State Machine:**
```
                    ┌───────────┐
                    │ UNMATCHED │  ← Initial state after parsing
                    └─────┬─────┘
                          │
            ┌─────────────┼─────────────────┐
            │ Matching    │ Matching         │ User ignores
            │ score≥80    │ score 60-79      │ (not reconcilable)
            ▼             ▼                  ▼
      ┌─────────┐   ┌───────────┐     ┌─────────┐
      │ MATCHED │   │ SUGGESTED │     │ IGNORED │
      └─────────┘   └─────┬─────┘     └─────────┘
                          │
                ┌─────────┼─────────┐
                │ ACCEPT  │ REJECT  │
                ▼         ▼         │
          ┌─────────┐   ┌───────────┐
          │ MATCHED │   │ UNMATCHED │ (back to queue)
          └─────────┘   └───────────┘
```

---

### 2.3 `bnk_vendor_mappings` — Learned Vendor Description Patterns

| # | Column | Type | Null | Default | Constraint | Description |
|---|--------|------|------|---------|------------|-------------|
| 1 | `id` | `UUID` | ❌ | `gen_random_uuid()` | **PK** | Primary key |
| 2 | `legal_entity_id` | `UUID` | ❌ | — | **FK** → `legal_entities(id)` ON DELETE CASCADE | Entity scope (country/bank specific) |
| 3 | `bank_description_pattern` | `VARCHAR(255)` | ❌ | — | `UNIQUE(entity_id, pattern)` | SQL LIKE pattern, e.g. `"AMZN%"` |
| 4 | `vendor_id` | `UUID` | ❌ | — | **FK** → `exp_vendors(id)` ON DELETE CASCADE | Mapped vendor |
| 5 | `confidence_level` | `VARCHAR(20)` | ❌ | `'USER_CONFIRMED'` | `CHECK (IN ('USER_CONFIRMED','AUTO_LEARNED'))` | Confidence level |
| 6 | `match_count` | `INTEGER` | ❌ | `1` | — | How many times this mapping has been used |
| 7 | `status` | `VARCHAR(20)` | ❌ | `'ACTIVE'` | `CHECK (IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))` | Soft-delete |
| 8 | `created_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Creation timestamp |
| 9 | `updated_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Last modified timestamp |

**Indexes:**

| Index Name | Columns | Purpose |
|-----------|---------|---------|
| `idx_bnk_vendor_map_entity` | `legal_entity_id` | Load all patterns for an entity |
| `idx_bnk_vendor_map_vendor` | `vendor_id` | Find patterns for a vendor |
| `uq_bnk_vendor_mapping` | `legal_entity_id, bank_description_pattern` | **UNIQUE** — one pattern per entity |

**Pattern Derivation Logic (in application layer):**
```
Bank Description          →    Pattern      →    Vendor
─────────────────────────────────────────────────────────
"AMZN MKTP US*1234ABC"   →    "AMZN%"      →    Amazon
"AWS*AMAZON WEB SERV"    →    "AWS%"       →    AWS (same vendor as Amazon)
"PAYPAL *EBAY INC"       →    "PAYPAL%"    →    PayPal
"GOOGLE*YOUTUBE PREMIUM" →    "GOOGLE%"    →    Google
"WF TRANSFER TO 884729"  →    (manual)     →    (user picks vendor)
```

---

### 2.4 `bnk_suggested_matches` — Medium-Confidence Match Suggestions

| # | Column | Type | Null | Default | Constraint | Description |
|---|--------|------|------|---------|------------|-------------|
| 1 | `id` | `UUID` | ❌ | `gen_random_uuid()` | **PK** | Primary key |
| 2 | `bank_transaction_id` | `UUID` | ❌ | — | **FK** → `bnk_transactions(id)` ON DELETE CASCADE | The bank transaction |
| 3 | `expense_transaction_id` | `UUID` | ❌ | — | **FK** → `exp_expense_transactions(id)` ON DELETE CASCADE | Suggested expense match |
| 4 | `matching_score` | `INTEGER` | ❌ | — | — | Score 60–79 |
| 5 | `score_breakdown` | `JSONB` | ✅ | — | — | `{"amount":50,"date":20,"description":5}` |
| 6 | `suggested_by` | `VARCHAR(20)` | ❌ | `'SYSTEM'` | `CHECK (IN ('SYSTEM','USER'))` | Who created the suggestion |
| 7 | `suggestion_status` | `VARCHAR(20)` | ❌ | `'PENDING'` | `CHECK (IN ('PENDING','ACCEPTED','REJECTED'))` | Resolution state |
| 8 | `resolved_by_shadow_user_id` | `UUID` | ✅ | — | **FK** → `shadow_users(id)` | User who resolved (nullable until resolved) |
| 9 | `resolved_at` | `TIMESTAMPTZ` | ✅ | — | — | When the suggestion was accepted/rejected |
| 10 | `status` | `VARCHAR(20)` | ❌ | `'ACTIVE'` | `CHECK (IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED'))` | Soft-delete |
| 11 | `created_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Creation timestamp |
| 12 | `updated_at` | `TIMESTAMPTZ` | ❌ | `NOW()` | — | Last modified timestamp |

**Indexes:**

| Index Name | Columns | Purpose |
|-----------|---------|---------|
| `idx_bnk_suggest_bank_txn` | `bank_transaction_id` | Find suggestions for a transaction |
| `idx_bnk_suggest_status` | `suggestion_status` | **Suggestion queue** — WHERE suggestion_status = 'PENDING' |

---

## 3. Modifications to Existing Tables

### 3.1 `exp_expense_transactions` — Add Reconciliation Columns

```sql
-- Migration V1.71
ALTER TABLE af_novadesk.exp_expense_transactions
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(30) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS matched_bank_transaction_id UUID DEFAULT NULL;

-- No FK constraint initially; added in a later migration after data validation
-- ALTER TABLE af_novadesk.exp_expense_transactions
--     ADD CONSTRAINT fk_et_matched_bank_txn 
--         FOREIGN KEY (matched_bank_transaction_id)
--         REFERENCES af_novadesk.bnk_transactions(id) ON DELETE SET NULL;
```

| Column | Type | Null | Purpose |
|--------|------|------|---------|
| `reconciliation_status` | `VARCHAR(30)` | ✅ | `NULL` = not applicable (capital injection expenses); `'RECONCILED'` = matched via bank reconciliation |
| `matched_bank_transaction_id` | `UUID` | ✅ | FK to `bnk_transactions(id)` — the bank transaction this expense was matched against |

**Why not modify `fa_ledger_entries`?**

[`LedgerEntry`](src/main/java/com/af/novadesk/api/finance/entity/LedgerEntry.java:45) is documented as **append-only / immutable**. Reconciliation is a mutable state change. The [`ExpenseTransaction`](src/main/java/com/af/novadesk/api/finance/entity/ExpenseTransaction.java:69) is the aggregate root for expenses and already has mutable status fields (`transaction_status`). Adding reconciliation fields here respects the existing architecture.

---

## 4. Complete Index Strategy

### 4.1 New Indexes (across all 4 new tables)

```
TABLE                   INDEX                                  TYPE        COLUMNS
──────────────────────────────────────────────────────────────────────────────────────────
bnk_statements          idx_bnk_statements_entity              BTREE       (legal_entity_id)
bnk_statements          idx_bnk_statements_bank_account        BTREE       (bank_account_id)
bnk_statements          idx_bnk_statements_period              BTREE       (bank_account_id, period_start, period_end)
bnk_statements          idx_bnk_statements_uploaded_by         BTREE       (uploaded_by_shadow_user_id)

bnk_transactions        idx_bnk_txn_statement                  BTREE       (statement_id)
bnk_transactions        idx_bnk_txn_entity                     BTREE       (legal_entity_id)
bnk_transactions        idx_bnk_txn_status                     BTREE       (reconciliation_status)
bnk_transactions        idx_bnk_txn_date                       BTREE       (transaction_date)
bnk_transactions        idx_bnk_txn_amount                     BTREE       (amount)
bnk_transactions        idx_bnk_txn_entity_status_date         BTREE       (legal_entity_id, reconciliation_status, transaction_date DESC)

bnk_vendor_mappings     idx_bnk_vendor_map_entity              BTREE       (legal_entity_id)
bnk_vendor_mappings     idx_bnk_vendor_map_vendor              BTREE       (vendor_id)
bnk_vendor_mappings     uq_bnk_vendor_mapping                  UNIQUE      (legal_entity_id, bank_description_pattern)

bnk_suggested_matches   idx_bnk_suggest_bank_txn               BTREE       (bank_transaction_id)
bnk_suggested_matches   idx_bnk_suggest_status                 BTREE       (suggestion_status)
```

### 4.2 Existing Indexes Relevant to Reconciliation Queries

```
TABLE                   INDEX                                  USED BY
──────────────────────────────────────────────────────────────────────────────────────────
exp_expense_transactions  idx_exp_txn_entity_date              Date-range matching within entity
exp_expense_transactions  idx_exp_txn_vendor_id                Vendor lookup during matching
fa_ledger_entries         idx_fa_ledger_entries_entity_date    Ledger lookups (if needed)
chart_of_accounts         idx_coa_account_type                 Filter EXPENSE-type accounts
entity_bank_accounts      idx_bank_account_legal_entity        Resolve bank account info
```

---

## 5. Key Query Patterns & Which Indexes They Use

### 5.1 Duplicate Detection (LLR-BNK-01.5)

```sql
-- Checked before upload to prevent duplicates
SELECT * FROM bnk_statements
WHERE bank_account_id = ?
  AND period_start = ?
  AND period_end = ?
  AND statement_status != 'SUPERSEDED'
  AND status = 'ACTIVE';
```
**Index used:** `idx_bnk_statements_period (bank_account_id, period_start, period_end)` — O(log n)

### 5.2 Unmatched Transaction Queue (LLR-BNK-03.1)

```sql
-- Finance user views unmatched transactions
SELECT * FROM bnk_transactions
WHERE legal_entity_id = ?
  AND reconciliation_status = 'UNMATCHED'
  AND transaction_date BETWEEN ? AND ?
ORDER BY transaction_date DESC
LIMIT 50 OFFSET ?;
```
**Index used:** `idx_bnk_txn_entity_status_date (legal_entity_id, reconciliation_status, transaction_date DESC)` — covering index

### 5.3 Matching Query (LLR-BNK-02.1)

```sql
-- Find candidate expenses for a bank transaction
SELECT * FROM exp_expense_transactions
WHERE legal_entity_id = ?
  AND reconciliation_status IS NULL        -- not yet reconciled
  AND expense_date BETWEEN ? AND ?         -- within ±3 days of bank date
ORDER BY expense_date DESC;
```
**Index used:** `idx_exp_txn_entity_date (legal_entity_id, expense_date DESC)` — existing

### 5.4 Vendor Pattern Lookup (LLR-BNK-02.5)

```sql
-- Find if a bank description matches a known vendor pattern
SELECT * FROM bnk_vendor_mappings
WHERE legal_entity_id = ?
  AND ? LIKE bank_description_pattern;     -- e.g., 'AMZN MKTP US*1234' LIKE 'AMZN%'
```
**Index used:** `idx_bnk_vendor_map_entity (legal_entity_id)` — narrows candidate set; LIKE evaluated on small result

### 5.5 Suggested Match Queue (LLR-BNK-02.4)

```sql
-- Finance user reviews pending suggestions
SELECT sm.*, bt.*, et.*
FROM bnk_suggested_matches sm
JOIN bnk_transactions bt ON sm.bank_transaction_id = bt.id
JOIN exp_expense_transactions et ON sm.expense_transaction_id = et.id
WHERE sm.suggestion_status = 'PENDING'
  AND bt.legal_entity_id = ?
ORDER BY sm.matching_score DESC
LIMIT 50 OFFSET ?;
```
**Index used:** `idx_bnk_suggest_status (suggestion_status)` — fast filter on PENDING

---

## 6. Data Flow Diagrams

### 6.1 Statement Upload → Transaction Extraction

```
┌──────────┐     ┌──────────────┐     ┌────────────┐     ┌──────────────────┐
│  Finance  │     │ BankRecon-   │     │  MinIO /   │     │   PostgreSQL     │
│  Operator │     │ ciliation    │     │    S3       │     │  (af_novadesk)   │
│           │     │ Controller   │     │             │     │                  │
└─────┬─────┘     └──────┬───────┘     └──────┬──────┘     └───────┬──────────┘
      │                  │                    │                     │
      │ POST /statements │                    │                     │
      │ /upload          │                    │                     │
      │ (multipart)      │                    │                     │
      ├─────────────────►│                    │                     │
      │                  │                    │                     │
      │                  │  Check duplicate   │                     │
      │                  ├─────────────────────────────────────────►│
      │                  │  SELECT * FROM bnk_statements            │
      │                  │  WHERE bank_account + period match       │
      │                  │◄─────────────────────────────────────────┤
      │                  │                    │                     │
      │                  │  Upload file       │                     │
      │                  ├───────────────────►│                     │
      │                  │  putObject(key,    │                     │
      │                  │    inputStream)    │                     │
      │                  │◄───────────────────┤                     │
      │                  │                    │                     │
      │                  │  INSERT bnk_statements (status=UPLOADED) │
      │                  ├─────────────────────────────────────────►│
      │                  │  ┌─ INSERT bnk_transactions (batch 500)  │
      │                  │  │  UPDATE bnk_statements                │
      │                  │  │  (transaction_count, status=PARSED)   │
      │                  ├──┴──────────────────────────────────────►│
      │                  │                    │                     │
      │                  │  [ASYNC] Trigger matching job            │
      │                  │                    │                     │
      │  201 Created     │                    │                     │
      │  (StatementDto)  │                    │                     │
      │◄─────────────────┤                    │                     │
```

### 6.2 Matching → Auto-Reconciliation

```
┌──────────────┐     ┌──────────────┐     ┌───────────────────┐
│  @Async      │     │  Matching    │     │    PostgreSQL      │
│  Job Runner  │     │  Engine      │     │   (af_novadesk)   │
└──────┬───────┘     └──────┬───────┘     └────────┬──────────┘
       │                    │                       │
       │ matchStatement()   │                       │
       ├───────────────────►│                       │
       │                    │                       │
       │                    │ SELECT UNMATCHED      │
       │                    │ bnk_transactions       │
       │                    ├──────────────────────►│
       │                    │◄──────────────────────┤
       │                    │                       │
       │                    │ SELECT unreconciled   │
       │                    │ exp_expense_transactions│
       │                    │ (same legal_entity)   │
       │                    ├──────────────────────►│
       │                    │◄──────────────────────┤
       │                    │                       │
       │                    │ SELECT vendor_mappings│
       │                    │ (same legal_entity)   │
       │                    ├──────────────────────►│
       │                    │◄──────────────────────┤
       │                    │                       │
       │   For each bank txn × expense txn:        │
       │   ┌─ calculateScore(bankTxn, expenseTxn)  │
       │   │                                        │
       │   ├─ score >= 80                          │
       │   │  ┌─ UPDATE bnk_transactions           │
       │   │  │  (status=MATCHED, score, method)   │
       │   │  │  UPDATE exp_expense_transactions   │
       │   │  │  (reconciliation_status=RECONCILED)│
       │   │  └───────────────────────────────────►│
       │   │                                        │
       │   ├─ score 60-79                          │
       │   │  ┌─ UPDATE bnk_transactions           │
       │   │  │  (status=SUGGESTED, score)         │
       │   │  │  INSERT bnk_suggested_matches      │
       │   │  └───────────────────────────────────►│
       │   │                                        │
       │   └─ score < 60                           │
       │      (no action — stays UNMATCHED)        │
       │                    │                       │
       │   COMPLETED        │                       │
       │◄───────────────────┤                       │
```

### 6.3 Manual Categorization → Double-Entry Posting

```
┌──────────┐     ┌──────────────┐     ┌───────────────────┐
│  Finance  │     │ Categorization│     │    PostgreSQL      │
│  Operator │     │   Service    │     │   (af_novadesk)   │
└─────┬─────┘     └──────┬───────┘     └────────┬──────────┘
      │                  │                       │
      │ POST /{id}/categorize                    │
      │ {vendorId, chartOfAccountId, notes}      │
      ├─────────────────►│                       │
      │                  │                       │
      │                  │ SELECT bank_txn       │
      │                  │ (verify UNMATCHED)    │
      │                  ├──────────────────────►│
      │                  │◄──────────────────────┤
      │                  │                       │
      │                  │ Validate:             │
      │                  │ - vendor exists       │
      │                  │ - chartOfAccount is   │
      │                  │   EXPENSE + postable  │
      │                  │ - belongs to entity   │
      │                  │                       │
      │                  │ ┌─ INSERT exp_expense_transactions
      │                  │ │  (reconciliation_status=RECONCILED,
      │                  │ │   matched_bank_transaction_id=?)
      │                  │ │
      │                  │ │  INSERT fa_ledger_entries (×2)
      │                  │ │  (CREDIT fa_account, DEBIT chart_of_account)
      │                  │ │
      │                  │ │  UPDATE bnk_transactions
      │                  │ │  (status=MATCHED, method=MANUAL)
      │                  │ │
      │                  │ │  UPSERT bnk_vendor_mappings
      │                  │ │  (learn pattern → vendor)
      │                  │ │
      │                  ├──┴──────────────────────►│
      │                  │    (ALL in one @Transactional)
      │                  │◄─────────────────────────┤
      │                  │                       │
      │  201 Created     │                       │
      │  (ExpenseTxnDto) │                       │
      │◄─────────────────┤                       │
```

---

## 7. Table Size Estimation

For capacity planning — estimates based on typical usage patterns:

| Table | Est. Rows/Year | Est. Row Size | Est. Annual Size | Growth Pattern |
|-------|---------------|---------------|-----------------|----------------|
| `bnk_statements` | 120 (10/month) | ~300 bytes | ~36 KB | Linear |
| `bnk_transactions` | 60,000 (500/statement) | ~200 bytes | ~12 MB | Linear |
| `bnk_vendor_mappings` | 500 | ~150 bytes | ~75 KB | Logarithmic (plateaus) |
| `bnk_suggested_matches` | 3,000 (5% of txns) | ~250 bytes | ~750 KB | Linear (temporary — purged after resolution) |

**Total annual growth:** ~13 MB (negligible for PostgreSQL)

---

## 8. Naming Convention Summary

Following the existing codebase conventions:

| Convention | Example | Applied To |
|-----------|---------|------------|
| Module prefix | `bnk_` | All 4 new tables |
| FK naming | `fk_bnk_stmt_entity` | Foreign key constraints |
| Index naming | `idx_bnk_txn_status` | Indexes |
| Unique constraint | `uq_bnk_vendor_mapping` | Unique constraints |
| Check constraint | `chk_bnk_stmt_status` | CHECK constraints |
| PK default | `DEFAULT gen_random_uuid()` | All PK columns |
| Timestamps | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | `created_at`, `updated_at` |
| Soft-delete | `status VARCHAR(20) DEFAULT 'ACTIVE'` | All entities |
| Schema | `af_novadesk` | All tables |

---

## 9. Migration Execution Order

```
V1.69__create_bnk_statements_table.sql
    ↓ (bnk_transactions depends on bnk_statements)
V1.70__create_bnk_transactions_table.sql
    ↓ (ALTER existing table — independent of above)
V1.71__add_reconciliation_to_expense_transactions.sql
    ↓ (depends on exp_vendors, legal_entities)
V1.72__create_bnk_vendor_mappings_table.sql
    ↓ (depends on bnk_transactions, exp_expense_transactions)
V1.73__create_bnk_suggested_matches_table.sql
```

All migrations are additive — no destructive changes to existing production tables. V1.71 only adds nullable columns to `exp_expense_transactions`.
