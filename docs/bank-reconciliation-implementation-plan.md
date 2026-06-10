



















































































































































# Bank Reconciliation — Implementation Plan

> **Author:** Senior Backend Engineer  
> **Date:** 2026-06-08  
> **Scope:** LLR-BNK-01, LLR-BNK-02, LLR-BNK-03  
> **Status:** Planning — No code changes yet

---

## Table of Contents

1. [Codebase Analysis Summary](#1-codebase-analysis-summary)
2. [Implementation Order & Dependencies](#2-implementation-order--dependencies)
3. [LLR-BNK-01: Secure Bank Statement Upload](#3-llr-bnk-01-secure-bank-statement-upload)
4. [LLR-BNK-02: Intelligent Transaction Matching](#4-llr-bnk-02-intelligent-transaction-matching)
5. [LLR-BNK-03: Manual Transaction Categorization](#5-llr-bnk-03-manual-transaction-categorization)
6. [Security Configuration Updates](#6-security-configuration-updates)
7. [Maven Dependencies to Add](#7-maven-dependencies-to-add)
8. [Application Configuration](#8-application-configuration)
9. [Testing Strategy](#9-testing-strategy)
10. [Risk Assessment & Edge Cases](#10-risk-assessment--edge-cases)

---

## 1. Codebase Analysis Summary

### 1.1 Existing Architecture

| Layer | Convention | Example |
|-------|-----------|---------|
| **Framework** | Spring Boot 3.x + JPA/Hibernate | [`NovaDeskapiApplication.java`](src/main/java/com/af/novadesk/api/NovaDeskapiApplication.java:1) |
| **Database** | PostgreSQL via Flyway migrations | [`V1.55__add_organization_id_to_exchange_rates.sql`](src/main/resources/db/migration/V1.55__add_organization_id_to_exchange_rates.sql:1) |
| **Schema** | `af_novadesk` (primary), `af_novadesk_outbox` (events) | All entities use `@Table(schema = "af_novadesk")` |
| **ORM** | Hibernate with `ddl-auto: validate` | JPA entities define the schema; Flyway owns DDL |
| **Auth** | JWT via AuthHub, permission-based `@PreAuthorize` | [`FinanceSecurityConfig.java`](src/main/java/com/af/novadesk/api/finance/security/FinanceSecurityConfig.java:96) |
| **Multi-tenancy** | `organizationId` UUID + Hibernate `@Filter` | [`AbstractEntity.java`](src/main/java/com/af/novadesk/api/common/entity/AbstractEntity.java:27) |
| **File Storage** | MinIO / S3 via `FileStorageService` | [`FileStorageService.java`](src/main/java/com/af/novadesk/api/common/service/FileStorageService.java:33) |
| **Events** | Outbox pattern (separate schema + poller) | e.g., [`ExpenseTransactionOutboxEvent`](src/main/java/com/af/novadesk/api/finance/entity/ExpenseTransactionOutboxEvent.java:1) |
| **JSON** | `SNAKE_CASE` property naming | `spring.jackson.property-naming-strategy: SNAKE_CASE` |
| **API Version** | `/api/v1/` prefix | All controllers |
| **Error Format** | RFC 7807 Problem Details | [`FinanceControllerAdvice.java`](src/main/java/com/af/novadesk/api/finance/controller/FinanceControllerAdvice.java:42) |
| **Response Envelope** | `ApiResponse<T>` via `ResponseBuilder` | [`ApiResponse.java`](src/main/java/com/af/novadesk/api/common/response/ApiResponse.java:1) |

### 1.2 Key Patterns to Follow

1. **Entity base class:** All entities extend [`AbstractEntity`](src/main/java/com/af/novadesk/api/common/entity/AbstractEntity.java:35) → gets `id` (UUID), `createdAt`, `updatedAt`, `status` (ACTIVE/INACTIVE).
2. **Org-scoping:** Entities that contain org-scoped data must use `@Filter(name = "organizationFilter", condition = "...")`. The condition must navigate to `legal_entities.organization_id` or use a direct `organization_id` column.
3. **Naming:** Table names follow the module prefix convention: `fa_` (funding/finance), `exp_` (expense), `pr_` (payroll), `ast_` (asset). **Bank reconciliation tables should use `bnk_` prefix.**
4. **Service layer:** Interface in `service/` package, implementation in `service/impl/`. Use `@Transactional(readOnly = true)` on the class, `@Transactional` on mutating methods.
5. **Security context:** `FinanceSecurityContext` provides `getOrganizationId()`, `getAuthUserId()`, `getEmail()`, `getDisplayName()`.
6. **Mapper pattern:** Separate mapper classes in `mapper/` package for entity ↔ DTO conversion.
7. **Exception handling:** Extend `FinanceBaseException` for new exception types. Register handlers in `FinanceExceptionHandler`.
8. **Flyway versions:** Next available version numbers start after the last migration (`V1.68`). Use `V1.69+` for new migrations.

### 1.3 Existing Entities Relevant to Bank Reconciliation

| Entity | Table | Key Fields | How It Relates |
|--------|-------|-----------|----------------|
| [`LegalEntity`](src/main/java/com/af/novadesk/api/finance/entity/LegalEntity.java:54) | `legal_entities` | `id`, `organizationId`, `entityCode`, `baseCurrency` | Parent scope for all bank accounts |
| [`EntityBankAccount`](src/main/java/com/af/novadesk/api/finance/entity/EntityBankAccount.java:43) | `entity_bank_accounts` | `legalEntity`, `accountType`, `bankName`, `accountNumber` | Bank accounts linked to entities |
| [`ChartOfAccount`](src/main/java/com/af/novadesk/api/finance/entity/ChartOfAccount.java:47) | `chart_of_accounts` | `legalEntity`, `accountCode`, `accountName`, `accountType` | Expense categories for categorization |
| [`Vendor`](src/main/java/com/af/novadesk/api/finance/entity/Vendor.java:56) | `exp_vendors` | `organizationId`, `vendorName`, `vendorType` | Payee registry for matching |
| [`LedgerEntry`](src/main/java/com/af/novadesk/api/finance/entity/LedgerEntry.java:45) | `fa_ledger_entries` | `legalEntity`, `account`, `chartOfAccount`, `entrySide`, `amountLocal` | Existing ledger that bank transactions reconcile against |
| [`ExpenseTransaction`](src/main/java/com/af/novadesk/api/finance/entity/ExpenseTransaction.java:69) | `exp_expense_transactions` | `legalEntity`, `vendor`, `amount`, `expenseDate`, `chartOfAccount` | Expenses that bank transactions can match against |
| [`ShadowUser`](src/main/java/com/af/novadesk/api/identity/entity/ShadowUser.java:1) | `shadow_users` | `authUserId`, `email`, `displayName` | User identity for audit trails |

### 1.4 LedgerEntry Reconciliation Considerations

The existing [`LedgerEntry`](src/main/java/com/af/novadesk/api/finance/entity/LedgerEntry.java:45) entity currently has **no reconciliation fields**. It needs to be extended. However, since [`LedgerEntry`](src/main/java/com/af/novadesk/api/finance/entity/LedgerEntry.java:45) serves both capital-injection (funding) and expense workflows, the reconciliation columns should be nullable so they only apply to expense-originated entries. An alternative is to reconcile at the [`ExpenseTransaction`](src/main/java/com/af/novadesk/api/finance/entity/ExpenseTransaction.java:69) level instead, which is cleaner because:

- The requirement says "Ledger entry: reconciliation_status = 'Reconciled', matched_bank_transaction_id = [id]"
- But ledger entries are append-only immutable records (per their Javadoc)
- Reconciling at the ExpenseTransaction level avoids mutating immutable ledger entries
- A bank transaction matches an ExpenseTransaction, which in turn has two LedgerEntry rows

**Decision: Add reconciliation fields to `ExpenseTransaction`** rather than `LedgerEntry`. This is architecturally cleaner and maintains the immutability of ledger entries.

---

## 2. Implementation Order & Dependencies

```
Phase 1: LLR-BNK-01 (Statement Upload)     ← Foundation, no dependencies
Phase 2: LLR-BNK-02 (Transaction Matching) ← Depends on BNK-01 entities + BNK-03 ledger creation
Phase 3: LLR-BNK-03 (Manual Categorization) ← Depends on BNK-01 entities
```

**Recommended build order within each phase:**

1. DB migrations (Flyway scripts)
2. Constants/Enums
3. Entities
4. DTOs
5. Repositories
6. Mappers
7. Services (interfaces, then implementations)
8. Controllers
9. Exception classes
10. Security config updates
11. Unit & integration tests

---

## 3. LLR-BNK-01: Secure Bank Statement Upload

### 3.1 Database Migrations

#### Migration V1.69: Create `bnk_statements` table

```sql
-- af_novadesk schema
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_statements (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id   UUID         NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    bank_account_id   UUID         NOT NULL REFERENCES af_novadesk.entity_bank_accounts(id) ON DELETE RESTRICT,
    uploaded_by_shadow_user_id UUID NOT NULL REFERENCES af_novadesk.shadow_users(id) ON DELETE RESTRICT,
    original_filename VARCHAR(255)  NOT NULL,
    storage_key       VARCHAR(500)  NOT NULL,
    file_type         VARCHAR(10)   NOT NULL,           -- CSV, XLSX, PDF
    file_size_bytes   INTEGER       NOT NULL,
    is_encrypted      BOOLEAN       NOT NULL DEFAULT true,
    period_start      DATE          NOT NULL,
    period_end        DATE          NOT NULL,
    transaction_count INTEGER       NOT NULL DEFAULT 0,
    notes             VARCHAR(500),
    statement_status  VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED',  -- UPLOADED, PARSED, SUPERSEDED, FAILED
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',    -- soft-delete

    CONSTRAINT chk_bnk_statements_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_statements_statement_status CHECK (statement_status IN ('UPLOADED','PARSED','SUPERSEDED','FAILED'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_bnk_statements_entity ON af_novadesk.bnk_statements (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_bank_account ON af_novadesk.bnk_statements (bank_account_id);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_period ON af_novadesk.bnk_statements (bank_account_id, period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_bnk_statements_uploaded_by ON af_novadesk.bnk_statements (uploaded_by_shadow_user_id);

COMMENT ON TABLE af_novadesk.bnk_statements IS 'Uploaded bank statements for reconciliation';
COMMENT ON COLUMN af_novadesk.bnk_statements.statement_status IS 'Lifecycle: UPLOADED→PARSED, or SUPERSEDED when replaced, FAILED when parsing fails';
```

#### Migration V1.70: Create `bnk_transactions` table

```sql
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_transactions (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id          UUID          NOT NULL REFERENCES af_novadesk.bnk_statements(id) ON DELETE CASCADE,
    legal_entity_id       UUID          NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE RESTRICT,
    bank_account_id       UUID          NOT NULL REFERENCES af_novadesk.entity_bank_accounts(id) ON DELETE RESTRICT,
    transaction_date      DATE          NOT NULL,
    description           VARCHAR(500)  NOT NULL,
    amount                DECIMAL(19,4) NOT NULL,          -- positive=credit(money in), negative=debit(money out)
    balance               DECIMAL(19,4),                   -- running balance from statement
    reconciliation_status VARCHAR(30)   NOT NULL DEFAULT 'UNMATCHED',  -- UNMATCHED, SUGGESTED, MATCHED, IGNORED
    matched_ledger_entry_id UUID,                          -- FK would be added later via ALTER TABLE
    matching_score        INTEGER,                         -- 0-100 from matching algorithm
    matching_method       VARCHAR(30),                     -- AUTO_MATCHED, USER_CONFIRMED, MANUAL
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_transactions_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_transactions_reconciliation_status CHECK (reconciliation_status IN ('UNMATCHED','SUGGESTED','MATCHED','IGNORED')),
    CONSTRAINT chk_bnk_transactions_matching_method CHECK (matching_method IS NULL OR matching_method IN ('AUTO_MATCHED','USER_CONFIRMED','MANUAL'))
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_bnk_txn_statement ON af_novadesk.bnk_transactions (statement_id);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_entity ON af_novadesk.bnk_transactions (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_status ON af_novadesk.bnk_transactions (reconciliation_status);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_date ON af_novadesk.bnk_transactions (transaction_date);
CREATE INDEX IF NOT EXISTS idx_bnk_txn_amount ON af_novadesk.bnk_transactions (amount);

COMMENT ON TABLE af_novadesk.bnk_transactions IS 'Individual transactions extracted from bank statements';
COMMENT ON COLUMN af_novadesk.bnk_transactions.amount IS 'Positive = credit (money in), Negative = debit (money out)';
COMMENT ON COLUMN af_novadesk.bnk_transactions.reconciliation_status IS 'UNMATCHED→SUGGESTED→MATCHED, or IGNORED';
```

#### Migration V1.71: Add reconciliation fields to `exp_expense_transactions`

```sql
ALTER TABLE af_novadesk.exp_expense_transactions
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(30) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS matched_bank_transaction_id UUID DEFAULT NULL;

-- No FK constraint initially; add after bnk_transactions is populated
-- ALTER TABLE af_novadesk.exp_expense_transactions
--     ADD CONSTRAINT fk_et_matched_bank_txn FOREIGN KEY (matched_bank_transaction_id)
--         REFERENCES af_novadesk.bnk_transactions(id) ON DELETE SET NULL;

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.reconciliation_status IS
    'NULL=not applicable, UNRECONCILED, RECONCILED. Only set for bank-reconciled expenses.';
COMMENT ON COLUMN af_novadesk.exp_expense_transactions.matched_bank_transaction_id IS
    'FK to bnk_transactions when this expense was matched via bank reconciliation.';
```

#### Migration V1.72: Create `bnk_vendor_mappings` table

```sql
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_vendor_mappings (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id         UUID         NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE,
    bank_description_pattern VARCHAR(255) NOT NULL,         -- e.g., "AMZN%", "AWS*%"
    vendor_id               UUID         NOT NULL REFERENCES af_novadesk.exp_vendors(id) ON DELETE CASCADE,
    confidence_level        VARCHAR(20)  NOT NULL DEFAULT 'USER_CONFIRMED',  -- USER_CONFIRMED, AUTO_LEARNED
    match_count             INTEGER      NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_vendor_mappings_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_vendor_mappings_confidence CHECK (confidence_level IN ('USER_CONFIRMED','AUTO_LEARNED')),
    CONSTRAINT uq_bnk_vendor_mapping UNIQUE (legal_entity_id, bank_description_pattern)
);

CREATE INDEX IF NOT EXISTS idx_bnk_vendor_map_entity ON af_novadesk.bnk_vendor_mappings (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_vendor_map_vendor ON af_novadesk.bnk_vendor_mappings (vendor_id);

COMMENT ON TABLE af_novadesk.bnk_vendor_mappings IS 'Learned vendor name patterns from bank statement descriptions';
COMMENT ON COLUMN af_novadesk.bnk_vendor_mappings.bank_description_pattern IS 'SQL LIKE pattern, e.g. AMZN% or %AWS%';
```

#### Migration V1.73: Create `bnk_suggested_matches` table

```sql
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_suggested_matches (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_transaction_id   UUID         NOT NULL REFERENCES af_novadesk.bnk_transactions(id) ON DELETE CASCADE,
    expense_transaction_id UUID        NOT NULL REFERENCES af_novadesk.exp_expense_transactions(id) ON DELETE CASCADE,
    matching_score        INTEGER      NOT NULL,              -- 60-79 range
    score_breakdown       JSONB,                              -- {"amount":50,"date":20,"description":5}
    suggested_by          VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM, USER
    suggestion_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED
    resolved_by_shadow_user_id UUID,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_suggestions_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_suggestions_suggestion_status CHECK (suggestion_status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT chk_bnk_suggestions_suggested_by CHECK (suggested_by IN ('SYSTEM','USER'))
);

CREATE INDEX IF NOT EXISTS idx_bnk_suggest_bank_txn ON af_novadesk.bnk_suggested_matches (bank_transaction_id);
CREATE INDEX IF NOT EXISTS idx_bnk_suggest_status ON af_novadesk.bnk_suggested_matches (suggestion_status);
```

### 3.2 New Enums

**Package:** `com.af.novadesk.api.finance.constants`

| Enum | Values | Purpose |
|------|--------|---------|
| `StatementStatus` | `UPLOADED, PARSED, SUPERSEDED, FAILED` | Lifecycle of an uploaded statement |
| `ReconciliationStatus` | `UNMATCHED, SUGGESTED, MATCHED, IGNORED` | Matching state of a bank transaction |
| `MatchingMethod` | `AUTO_MATCHED, USER_CONFIRMED, MANUAL` | How the match was established |
| `SuggestionStatus` | `PENDING, ACCEPTED, REJECTED` | State of a suggested match |
| `VendorMappingConfidence` | `USER_CONFIRMED, AUTO_LEARNED` | Confidence level of vendor pattern mapping |

Existing `ExpenseTransactionStatus` needs a new value consideration — but since reconciliation is additive (doesn't change the POSTED/VOID lifecycle), the reconciliation status is a separate field.

### 3.3 New Entities

All entities in package `com.af.novadesk.api.finance.entity`.

#### `BankStatement` — maps to `bnk_statements`

```java
@Entity
@Table(name = "bnk_statements", schema = "af_novadesk")
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "bankAccount", "uploadedBy", "transactions"})
public class BankStatement extends AbstractEntity {
    // @ManyToOne LegalEntity legalEntity
    // @ManyToOne EntityBankAccount bankAccount
    // @ManyToOne ShadowUser uploadedBy
    // String originalFilename
    // String storageKey
    // String fileType           -- CSV, XLSX, PDF
    // Integer fileSizeBytes
    // Boolean isEncrypted (default true)
    // LocalDate periodStart
    // LocalDate periodEnd
    // Integer transactionCount (default 0)
    // String notes (max 500)
    // @Enumerated StatementStatus statementStatus (default UPLOADED)
    // @OneToMany List<BankTransaction> transactions
}
```

#### `BankTransaction` — maps to `bnk_transactions`

```java
@Entity
@Table(name = "bnk_transactions", schema = "af_novadesk")
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"statement", "legalEntity", "bankAccount"})
public class BankTransaction extends AbstractEntity {
    // @ManyToOne BankStatement statement
    // @ManyToOne LegalEntity legalEntity
    // @ManyToOne EntityBankAccount bankAccount
    // LocalDate transactionDate
    // String description (max 500)
    // BigDecimal amount            -- positive=credit, negative=debit
    // BigDecimal balance           -- running balance
    // @Enumerated ReconciliationStatus reconciliationStatus (default UNMATCHED)
    // UUID matchedLedgerEntryId    -- FK to fa_ledger_entries (nullable, set after match)
    // Integer matchingScore        -- 0-100
    // @Enumerated MatchingMethod matchingMethod
}
```

#### `VendorMapping` — maps to `bnk_vendor_mappings`

```java
@Entity
@Table(name = "bnk_vendor_mappings", schema = "af_novadesk")
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "vendor"})
public class VendorMapping extends AbstractEntity {
    // @ManyToOne LegalEntity legalEntity
    // String bankDescriptionPattern   -- SQL LIKE pattern
    // @ManyToOne Vendor vendor
    // @Enumerated VendorMappingConfidence confidenceLevel (default USER_CONFIRMED)
    // Integer matchCount (default 1)
}
```

#### `SuggestedMatch` — maps to `bnk_suggested_matches`

```java
@Entity
@Table(name = "bnk_suggested_matches", schema = "af_novadesk")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"bankTransaction", "expenseTransaction", "resolvedBy"})
public class SuggestedMatch extends AbstractEntity {
    // @ManyToOne BankTransaction bankTransaction
    // @ManyToOne ExpenseTransaction expenseTransaction
    // Integer matchingScore          -- 60-79 range
    // String scoreBreakdown          -- JSON string: {"amount":50,"date":20,"description":5}
    // @Enumerated SuggestedBy suggestedBy (default SYSTEM)
    // @Enumerated SuggestionStatus suggestionStatus (default PENDING)
    // @ManyToOne ShadowUser resolvedBy
    // LocalDateTime resolvedAt
}
```

### 3.4 New DTOs

**Package:** `com.af.novadesk.api.finance.dto`

| DTO | Purpose |
|-----|---------|
| `BankStatementUploadRequest` | Form fields for uploading: `entityId`, `bankAccountId`, `periodStart`, `periodEnd`, `notes`, `filePassword` |
| `BankStatementDto` | Statement metadata for list/detail views |
| `BankStatementPageDto` | Paginated statement list response |
| `BankTransactionDto` | Individual transaction with reconciliation status |
| `BankTransactionPageDto` | Paginated transaction list response |
| `DuplicateStatementWarningDto` | Warning response when duplicate detected |
| `CategorizeTransactionRequest` | Manual categorization form: `vendorId`, `chartOfAccountId`, `department`, `project`, `notes` |
| `BulkCategorizeRequest` | `List<UUID> transactionIds`, `vendorId`, `chartOfAccountId` |
| `SplitTransactionRequest` | `List<SplitLineItem>` where each has `vendorId`, `chartOfAccountId`, `amount` |
| `SuggestedMatchDto` | Suggestion for user review |
| `SuggestedMatchPageDto` | Paginated suggestions |
| `ResolveSuggestionRequest` | `action`: ACCEPT / REJECT / FIND_DIFFERENT |
| `MatchingScoreBreakdown` | `amountScore`, `dateScore`, `descriptionScore`, `totalScore` |

### 3.5 File Parsing Strategy

Two new service classes in `com.af.novadesk.api.finance.service`:

#### `BankStatementParser` (interface) + implementations

```
BankStatementParser (interface)
├── CsvStatementParser      — OpenCSV-based parser
├── ExcelStatementParser    — Apache POI-based parser
└── (future) PdfStatementParser — OCR-based
```

**Parser interface:**
```java
public interface BankStatementParser {
    boolean supports(String fileType);  // "CSV", "XLSX"
    List<ParsedTransaction> parse(InputStream input, String password) throws ParseException;
}

// Internal record, not a DTO:
record ParsedTransaction(
    LocalDate transactionDate,
    String description,
    BigDecimal debit,      // null if credit
    BigDecimal credit,     // null if debit
    BigDecimal balance
) {}
```

**Factory pattern** for selecting parser based on file extension:
```java
@Service
public class BankStatementParserFactory {
    private final List<BankStatementParser> parsers;
    // Auto-injected via constructor — Spring collects all BankStatementParser beans
    
    public BankStatementParser getParser(String fileType) {
        return parsers.stream()
            .filter(p -> p.supports(fileType))
            .findFirst()
            .orElseThrow(() -> new BadRequestException("Unsupported file type: " + fileType));
    }
}
```

**CSV Parser Implementation Notes:**
- Use OpenCSV (`com.opencsv:opencsv:5.9`) — needs to be added to [`pom.xml`](pom.xml:1)
- Auto-detect delimiter (comma, tab, semicolon)
- Auto-detect columns by header name matching:
  - Date column: matches `date`, `transaction date`, `posting date`, `value date` (case-insensitive)
  - Description column: matches `description`, `narration`, `particulars`, `memo`, `details`
  - Debit column: matches `debit`, `withdrawal`, `money out`, `dr`
  - Credit column: matches `credit`, `deposit`, `money in`, `cr`
  - Balance column: matches `balance`, `running balance`
- If only one amount column exists (signed amounts), infer debit/credit from sign
- Validate: at least date + description + one amount column must be found

**Excel Parser Implementation Notes:**
- Use Apache POI (`org.apache.poi:poi-ooxml:5.3.0`) — needs to be added to [`pom.xml`](pom.xml:1)
- Read first sheet; skip header row; auto-detect columns same as CSV
- Handle both `.xlsx` (XSSFWorkbook) and `.xls` (HSSFWorkbook)

### 3.6 Duplicate Detection Logic

In [`BankStatementService`](src/main/java/com/af/novadesk/api/finance/service/BankStatementService.java) (new):

```java
// Before parsing, check for duplicates:
// SELECT * FROM bnk_statements 
// WHERE bank_account_id = :bankAccountId 
//   AND period_start = :periodStart 
//   AND period_end = :periodEnd
//   AND statement_status != 'SUPERSEDED'
//   AND status = 'ACTIVE'

// If found, return DuplicateStatementWarningDto with:
// - Existing statement ID
// - Uploaded date
// - Uploaded by (user display name)
// - Option: REPLACE or CANCEL

// If REPLACE chosen:
// - Mark existing statement as SUPERSEDED
// - Proceed with upload
```

**Content-based deduplication (future enhancement):** Compute SHA-256 hash of file content before parsing. If an identical file was uploaded before (same hash + same bank account), warn even if period differs.

### 3.7 Service Layer Design

#### `BankStatementService` (interface)

```java
public interface BankStatementService {
    // LLR-BNK-01.1: Upload + parse
    BankStatementDto uploadStatement(BankStatementUploadRequest request, MultipartFile file);
    
    // LLR-BNK-01.4: Parse already-uploaded file
    BankStatementDto parseStatement(UUID statementId);
    
    // Check duplicate before upload
    DuplicateStatementWarningDto checkDuplicate(UUID bankAccountId, LocalDate periodStart, LocalDate periodEnd);
    
    // Replace existing statement
    BankStatementDto replaceStatement(UUID existingStatementId, MultipartFile file, String notes);
    
    // List statements
    BankStatementPageDto listStatements(UUID entityId, int page, int size, String sortBy);
    
    // Get statement detail with transactions
    BankStatementDto getStatement(UUID statementId);
    
    // Get transactions for a statement
    BankTransactionPageDto getTransactions(UUID statementId, int page, int size, String reconciliationStatus);
}
```

#### `BankStatementServiceImpl` — Key Workflow

```
uploadStatement(request, file):
  1. Extract orgId, authUserId from FinanceSecurityContext
  2. Validate file type (CSV, XLSX) and size (max configurable, default 10 MB)
  3. Resolve LegalEntity (org-scoped) — throw EntityNotFoundException
  4. Resolve EntityBankAccount — validate it belongs to the entity
  5. Resolve ShadowUser
  6. Check for duplicate statement (same bank account + period)
     → If duplicate: return warning, don't proceed
  7. Upload file to MinIO via FileStorageService:
     storageKey = "{orgId}/bank-statements/{entityId}/{uuid}.{ext}"
  8. Persist BankStatement entity with status=UPLOADED
  9. Parse file using BankStatementParserFactory
  10. Extract transactions, convert to BankTransaction entities
  11. Persist all BankTransaction entities (batch insert, 500 per batch)
  12. Update statement: transactionCount, status=PARSED
  13. [ASYNC] Trigger matching job for new transactions (LLR-BNK-02)
  14. Return BankStatementDto
```

### 3.8 Controller Design

#### `BankReconciliationController`

```java
@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class BankReconciliationController {
    
    private final BankStatementService bankStatementService;
    private final BankMatchingService bankMatchingService;      // BNK-02
    private final BankCategorizationService categorizationService; // BNK-03
    
    // ── LLR-BNK-01: Statement Upload ──────────────────────────
    
    @PostMapping("/statements/upload")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<BankStatementDto>> uploadStatement(
            @RequestPart("request") BankStatementUploadRequest request,
            @RequestPart("file") MultipartFile file) {
        // ...
    }
    
    @PostMapping("/statements/{id}/parse")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<BankStatementDto>> parseStatement(@PathVariable UUID id) {
        // ...
    }
    
    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<BankStatementPageDto>> listStatements(
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // ...
    }
    
    @GetMapping("/statements/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<BankStatementDto>> getStatement(@PathVariable UUID id) {
        // ...
    }
    
    @GetMapping("/statements/{id}/transactions")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<BankTransactionPageDto>> getTransactions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String reconciliationStatus) {
        // ...
    }
    
    // ── LLR-BNK-02: Matching ──────────────────────────────────
    // (see section 4)
    
    // ── LLR-BNK-03: Manual Categorization ─────────────────────
    // (see section 5)
}
```

### 3.9 Exception Classes

New exceptions in `com.af.novadesk.api.finance.exception`:

| Exception | HTTP Status | Use Case |
|-----------|------------|----------|
| `StatementNotFoundException` | 404 | Statement ID not found |
| `DuplicateStatementException` | 409 | Same bank account + period already exists |
| `StatementParseException` | 422 | File parsing failed (malformed CSV/Excel) |
| `UnsupportedFileTypeException` | 400 | File type not CSV/XLSX |
| `BankTransactionNotFoundException` | 404 | Transaction ID not found |

Each should extend `FinanceBaseException` or be handled by [`FinanceExceptionHandler`](src/main/java/com/af/novadesk/api/finance/exception/FinanceExceptionHandler.java:1).

### 3.10 File Validation & Security

1. **File type check:** Only `.csv`, `.xlsx`, `.xls` allowed (validated by extension + magic bytes).
2. **File size limit:** Configurable via `app.bank-reconciliation.max-file-size-bytes` (default 10 MB).
3. **Virus scanning:** (future) Integrate ClamAV — scan before storing. For now, document as future enhancement.
4. **Storage:** Files stored in MinIO with `isEncrypted = true`. Storage key follows convention: `{orgId}/bank-statements/{entityId}/{uuid}.{ext}`.
5. **Access control:** Only users with Finance role can view/download statements (enforced via `@PreAuthorize`).
6. **File password:** The `filePassword` field in the upload form is stored as metadata only. For encrypted Excel files, Apache POI's `WorkbookFactory.create(file, password)` is used. The password is NOT stored in the database — it's used in-memory during parsing only.

---

## 4. LLR-BNK-02: Intelligent Transaction Matching

### 4.1 Matching Algorithm Design

#### Service: `BankMatchingService`

```java
public interface BankMatchingService {
    // Run matching for a specific statement (called after parsing)
    void matchStatement(UUID statementId);
    
    // Run matching for a single transaction (called after manual categorization)
    void matchTransaction(UUID bankTransactionId);
    
    // Get suggested matches for user review
    SuggestedMatchPageDto getSuggestedMatches(UUID entityId, int page, int size);
    
    // Accept/reject a suggestion
    SuggestedMatchDto resolveSuggestion(UUID suggestionId, ResolveSuggestionRequest request);
}
```

#### Matching Algorithm Flow

```
matchStatement(statementId):
  1. Load all UNMATCHED BankTransactions for the statement
  2. For each transaction:
     a. Compute matching score against all unreconciled ExpenseTransactions
        in the same LegalEntity
     b. If score >= 80 → auto-match
     c. If score 60-79 → create SuggestedMatch
     d. If score < 60 → leave as UNMATCHED

matchTransaction(bankTransaction):
  1. Compute scores against all unreconciled ExpenseTransactions
  2. Same threshold logic as above
```

#### Scoring Calculation

```java
public class MatchingScoreCalculator {
    
    /**
     * Calculate matching score between a bank transaction and an expense transaction.
     * 
     * @return score 0-100
     */
    public int calculate(BankTransaction bankTxn, ExpenseTransaction expenseTxn) {
        int score = 0;
        MatchingScoreBreakdown breakdown = new MatchingScoreBreakdown();
        
        // ── Amount Match (50 points) ──────────────────────────
        // Bank amount: positive=credit (money in), negative=debit (money out)
        // Expense amount: always positive (money out from entity perspective)
        // So we compare ABS(bankTxn.amount) with expenseTxn.amount
        // The bank debit should equal the expense amount
        BigDecimal bankAbsAmount = bankTxn.getAmount().abs();
        BigDecimal expenseAmount = expenseTxn.getAmount();
        
        if (bankAbsAmount.compareTo(expenseAmount) == 0) {
            score += 50;
            breakdown.setAmountScore(50);
        }
        // No partial amount match — amounts must be exact
        
        // ── Date Match (30 points) ────────────────────────────
        long daysDiff = Math.abs(ChronoUnit.DAYS.between(
            bankTxn.getTransactionDate(), expenseTxn.getExpenseDate()));
        
        if (daysDiff == 0) {
            score += 30;
            breakdown.setDateScore(30);
        } else if (daysDiff <= 1) {
            score += 20;
            breakdown.setDateScore(20);
        } else if (daysDiff <= 3) {
            score += 10;
            breakdown.setDateScore(10);
        }
        // > 3 days: 0 points
        
        // ── Description Similarity (20 points) ────────────────
        // Step 1: Check vendor name match
        String bankDesc = bankTxn.getDescription().toLowerCase().trim();
        String vendorName = expenseTxn.getVendor().getVendorName().toLowerCase().trim();
        
        // Step 2: Check vendor mappings (learned patterns)
        Optional<VendorMapping> mapping = vendorMappingRepository
            .findByEntityAndPatternMatch(expenseTxn.getLegalEntity().getId(), bankDesc);
        
        // Step 3: Fuzzy string matching
        int descScore = 0;
        if (mapping.isPresent()) {
            // Known vendor pattern match → full points
            descScore = 20;
        } else if (containsVendorName(bankDesc, vendorName)) {
            // Bank description contains vendor name
            descScore = 15;
        } else {
            // Levenshtein distance on cleaned description
            int distance = StringUtils.getLevenshteinDistance(
                cleanDescription(bankDesc), 
                cleanDescription(vendorName)
            );
            if (distance < 5) {
                descScore = 10;
            } else if (distance < 10 && bankDesc.contains(extractKeywords(vendorName))) {
                descScore = 5;
            }
        }
        score += descScore;
        breakdown.setDescriptionScore(descScore);
        
        breakdown.setTotalScore(score);
        return score;
    }
    
    private String cleanDescription(String desc) {
        // Remove dates, reference numbers, common prefixes
        return desc.replaceAll("[0-9]{2}/[0-9]{2}/[0-9]{4}", "")
                   .replaceAll("REF#?\\s*[A-Z0-9]+", "")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
    
    private boolean containsVendorName(String bankDesc, String vendorName) {
        // Split vendor name into tokens, check if all meaningful tokens appear
        String[] tokens = vendorName.split("\\s+");
        return Arrays.stream(tokens)
            .filter(t -> t.length() > 2)
            .allMatch(t -> bankDesc.contains(t));
    }
}
```

### 4.2 Auto-Match Execution

When score >= 80:

```java
@Transactional
public void executeAutoMatch(BankTransaction bankTxn, ExpenseTransaction expenseTxn, int score) {
    // 1. Update BankTransaction
    bankTxn.setReconciliationStatus(ReconciliationStatus.MATCHED);
    bankTxn.setMatchingScore(score);
    bankTxn.setMatchingMethod(MatchingMethod.AUTO_MATCHED);
    // matchedLedgerEntryId: set to the DEBIT ledger entry of the expense
    // (the one with chartOfAccount != null)
    bankTxn.setMatchedLedgerEntryId(findDebitLedgerEntry(expenseTxn).getId());
    bankTransactionRepository.save(bankTxn);
    
    // 2. Update ExpenseTransaction
    expenseTxn.setReconciliationStatus("RECONCILED");
    expenseTxn.setMatchedBankTransactionId(bankTxn.getId());
    expenseTransactionRepository.save(expenseTxn);
    
    log.info("Auto-matched bank transaction {} to expense {} (score={})",
        bankTxn.getId(), expenseTxn.getId(), score);
}
```

### 4.3 Suggested Match Creation

When score 60-79:

```java
@Transactional
public void createSuggestion(BankTransaction bankTxn, ExpenseTransaction expenseTxn, 
                              int score, MatchingScoreBreakdown breakdown) {
    // 1. Check if suggestion already exists for this pair
    if (suggestedMatchRepository.existsByBankTransactionAndExpenseTransaction(
            bankTxn.getId(), expenseTxn.getId())) {
        return; // Don't create duplicate suggestions
    }
    
    // 2. Update BankTransaction to SUGGESTED status
    bankTxn.setReconciliationStatus(ReconciliationStatus.SUGGESTED);
    bankTxn.setMatchingScore(score);
    bankTransactionRepository.save(bankTxn);
    
    // 3. Create SuggestedMatch record
    SuggestedMatch suggestion = SuggestedMatch.builder()
        .bankTransaction(bankTxn)
        .expenseTransaction(expenseTxn)
        .matchingScore(score)
        .scoreBreakdown(toJson(breakdown))  // serialize to JSON string
        .suggestedBy(SuggestedBy.SYSTEM)
        .suggestionStatus(SuggestionStatus.PENDING)
        .build();
    suggestedMatchRepository.save(suggestion);
}
```

### 4.4 Vendor Recognition Learning

When a user manually matches or confirms a match:

```java
@Transactional
public void learnVendorMapping(BankTransaction bankTxn, Vendor vendor) {
    String description = bankTxn.getDescription().trim();
    String pattern = derivePattern(description);
    
    // Check if pattern already exists for this entity
    Optional<VendorMapping> existing = vendorMappingRepository
        .findByLegalEntityAndPattern(bankTxn.getLegalEntity().getId(), pattern);
    
    if (existing.isPresent()) {
        VendorMapping mapping = existing.get();
        mapping.setMatchCount(mapping.getMatchCount() + 1);
        if (mapping.getMatchCount() >= 3) {
            mapping.setConfidenceLevel(VendorMappingConfidence.AUTO_LEARNED);
        }
        vendorMappingRepository.save(mapping);
    } else {
        VendorMapping mapping = VendorMapping.builder()
            .legalEntity(bankTxn.getLegalEntity())
            .bankDescriptionPattern(pattern)
            .vendor(vendor)
            .confidenceLevel(VendorMappingConfidence.USER_CONFIRMED)
            .matchCount(1)
            .build();
        vendorMappingRepository.save(mapping);
    }
}

/**
 * Derive a SQL LIKE pattern from a bank description.
 * Examples:
 *   "AMZN MKTP US*1234ABC" → "AMZN%"
 *   "AWS*AMAZON WEB SERV" → "AWS%"
 *   "PAYPAL *EBAY INC"    → "PAYPAL%"
 * 
 * Strategy: take the first word/token that is all-alphabetic
 * and at least 3 characters, then append '%'.
 */
private String derivePattern(String description) {
    String[] tokens = description.trim().split("[\\s\\*]+");
    for (String token : tokens) {
        String cleaned = token.replaceAll("[^A-Za-z]", "");
        if (cleaned.length() >= 3) {
            return cleaned + "%";
        }
    }
    // Fallback: first 5 chars + %
    return description.substring(0, Math.min(5, description.length())) + "%";
}
```

### 4.5 Batch Processing for Large Statements

For statements with 1000+ transactions:

```java
@Service
public class BankMatchingServiceImpl implements BankMatchingService {
    
    private static final int BATCH_SIZE = 200;
    
    @Override
    @Async  // Run asynchronously so the upload endpoint returns quickly
    @Transactional
    public void matchStatement(UUID statementId) {
        List<BankTransaction> unmatched = bankTransactionRepository
            .findByStatementIdAndReconciliationStatus(statementId, ReconciliationStatus.UNMATCHED);
        
        // Pre-load all unreconciled expenses for the entity (avoids N+1)
        UUID entityId = unmatched.get(0).getLegalEntity().getId();
        List<ExpenseTransaction> unreconciledExpenses = expenseTransactionRepository
            .findByLegalEntityIdAndReconciliationStatus(entityId, null); // null = not reconciled
        
        // Pre-load vendor mappings for the entity
        Map<String, UUID> vendorPatterns = loadVendorPatterns(entityId);
        
        for (int i = 0; i < unmatched.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, unmatched.size());
            List<BankTransaction> batch = unmatched.subList(i, end);
            processBatch(batch, unreconciledExpenses, vendorPatterns);
        }
    }
}
```

**Note:** `@Async` requires enabling `@EnableAsync` in configuration. Add to `CommonModuleConfig` or create a dedicated config.

---

## 5. LLR-BNK-03: Manual Transaction Categorization

### 5.1 Service Layer Design

#### `BankCategorizationService` (interface)

```java
public interface BankCategorizationService {
    // List unmatched transactions with filters
    BankTransactionPageDto getUnmatchedTransactions(
        UUID entityId, UUID bankAccountId, LocalDate dateFrom, LocalDate dateTo,
        BigDecimal amountMin, BigDecimal amountMax, String descriptionSearch,
        int page, int size, String sortBy, String sortDirection);
    
    // Categorize single transaction → creates ExpenseTransaction + LedgerEntries
    ExpenseTransactionDto categorizeTransaction(
        UUID bankTransactionId, CategorizeTransactionRequest request);
    
    // Bulk categorize
    List<ExpenseTransactionDto> bulkCategorize(BulkCategorizeRequest request);
    
    // Split transaction
    List<ExpenseTransactionDto> splitTransaction(
        UUID bankTransactionId, SplitTransactionRequest request);
    
    // Mark as ignored (not reconcilable)
    BankTransactionDto ignoreTransaction(UUID bankTransactionId, String reason);
}
```

### 5.2 Single Categorization Flow

```
categorizeTransaction(bankTransactionId, request):
  1. Extract orgId, authUserId from FinanceSecurityContext
  2. Load BankTransaction — verify it's UNMATCHED
  3. Validate request:
     - vendorId exists in org
     - chartOfAccountId exists in entity and is EXPENSE type and is postable
  4. Load LegalEntity, Vendor, ChartOfAccount, ShadowUser
  5. Create ExpenseTransaction (similar to ExpenseTransactionServiceImpl.recordExpense):
     - legalEntity = from bank transaction
     - vendor = from request
     - expenseDate = bankTransaction.transactionDate
     - amount = ABS(bankTransaction.amount)  [bank debits are negative]
     - paymentMethod = BANK_TRANSFER (default for bank-sourced)
     - sourceAccount = resolve the entity bank account's linked fa_account
     - chartOfAccount = from request
     - description = user notes OR bank transaction description
     - transactionStatus = POSTED
     - reconciliationStatus = "RECONCILED"
     - matchedBankTransactionId = bankTransactionId
  6. Save ExpenseTransaction
  7. Create double-entry LedgerEntries:
     - CREDIT source account (bank account)
     - DEBIT chart of account (expense category)
  8. Update BankTransaction:
     - reconciliationStatus = MATCHED
     - matchedLedgerEntryId = debit ledger entry ID
     - matchingMethod = MANUAL
     - matchingScore = 100
  9. Learn vendor mapping (LLR-BNK-02.5):
     - derive pattern from bank description
     - upsert VendorMapping
  10. Return ExpenseTransactionDto
```

### 5.3 How to Resolve the Source Account

The bank transaction comes from a specific [`EntityBankAccount`](src/main/java/com/af/novadesk/api/finance/entity/EntityBankAccount.java:43). Entity bank accounts are metadata records. The actual `Account` (fa_accounts) for double-entry posting needs to be resolved:

```java
private Account resolveSourceAccount(EntityBankAccount bankAccount, LegalEntity entity) {
    // Strategy: find the fa_account that matches the bank account type
    // EntityBankAccount.accountType (e.g., OPERATING) → Account.accountRole (e.g., BANK_OPERATING)
    String accountRole = mapBankAccountTypeToAccountRole(bankAccount.getAccountType());
    return accountRepository.findByLegalEntityAndAccountRole(entity, accountRole)
        .orElseThrow(() -> new AccountNotFoundException(
            "No funding account found for bank account type: " + bankAccount.getAccountType()));
}

private String mapBankAccountTypeToAccountRole(BankAccountType type) {
    return switch (type) {
        case CASH      -> "CASH";
        case OPERATING -> "BANK_OPERATING";
        case PAYROLL   -> "BANK_OPERATING";  // fallback
        default        -> "BANK_OPERATING";
    };
}
```

### 5.4 Bulk Categorization Flow

```
bulkCategorize(request):
  1. Validate all transactionIds exist and are UNMATCHED
  2. Validate vendorId and chartOfAccountId
  3. For each transaction:
     - Create ExpenseTransaction (same as single, but batch-persist)
     - Create LedgerEntries
     - Update BankTransaction status
  4. Process in batches of 100 with @Transactional
  5. Return list of created ExpenseTransactionDtos
```

### 5.5 Split Transaction Flow

```
splitTransaction(bankTransactionId, request):
  1. Load BankTransaction — verify UNMATCHED
  2. Validate: SUM(split_lines.amount) == ABS(bankTransaction.amount)
     (within tolerance of 0.001 to handle rounding)
  3. For each split line:
     - Create ExpenseTransaction with line.amount
     - Create double-entry LedgerEntries
  4. Update BankTransaction: MATCHED, matchingMethod=MANUAL
  5. Return list of ExpenseTransactionDtos
```

### 5.6 Controller Endpoints (add to `BankReconciliationController`)

```java
// ── LLR-BNK-03: Manual Categorization ─────────────────────

@GetMapping("/transactions/unmatched")
@PreAuthorize("hasAuthority('organizations:write')")
public ResponseEntity<ApiResponse<BankTransactionPageDto>> getUnmatchedTransactions(
        @RequestParam UUID entityId,
        @RequestParam(required = false) UUID bankAccountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) BigDecimal amountMin,
        @RequestParam(required = false) BigDecimal amountMax,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(defaultValue = "transactionDate") String sortBy,
        @RequestParam(defaultValue = "DESC") String sortDirection) {
    // ...
}

@PostMapping("/transactions/{id}/categorize")
@PreAuthorize("hasAuthority('organizations:write')")
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<ApiResponse<ExpenseTransactionDto>> categorizeTransaction(
        @PathVariable UUID id,
        @Valid @RequestBody CategorizeTransactionRequest request) {
    // ...
}

@PostMapping("/transactions/bulk-categorize")
@PreAuthorize("hasAuthority('organizations:write')")
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<ApiResponse<List<ExpenseTransactionDto>>> bulkCategorize(
        @Valid @RequestBody BulkCategorizeRequest request) {
    // ...
}

@PostMapping("/transactions/{id}/split")
@PreAuthorize("hasAuthority('organizations:write')")
@ResponseStatus(HttpStatus.CREATED)
public ResponseEntity<ApiResponse<List<ExpenseTransactionDto>>> splitTransaction(
        @PathVariable UUID id,
        @Valid @RequestBody SplitTransactionRequest request) {
    // ...
}

@PostMapping("/transactions/{id}/ignore")
@PreAuthorize("hasAuthority('organizations:write')")
public ResponseEntity<ApiResponse<BankTransactionDto>> ignoreTransaction(
        @PathVariable UUID id,
        @RequestParam String reason) {
    // ...
}
```

### 5.7 Additional Exception Classes

| Exception | HTTP Status | Use Case |
|-----------|------------|----------|
| `TransactionAlreadyMatchedException` | 422 | Attempting to categorize an already-matched transaction |
| `SplitAmountMismatchException` | 400 | Split amounts don't sum to bank transaction amount |
| `InvalidCategorizationException` | 400 | Invalid vendor/account for the entity |

---

## 6. Security Configuration Updates

### 6.1 URL Patterns

Add to [`FinanceSecurityConfig.java`](src/main/java/com/af/novadesk/api/finance/security/FinanceSecurityConfig.java:101):

```java
.securityMatcher(
    "/api/v1/finance/**",
    "/api/v1/legal-entities/**",
    "/api/v1/expense/**",
    "/api/v1/assets/**",
    "/api/v1/employees/**",
    "/api/v1/reconciliation/**",    // ← NEW: Bank reconciliation endpoints
    "/api/v1/payroll/**"            // Already added for payroll module
)
```

### 6.2 CORS Configuration

Add to CORS configuration source:

```java
source.registerCorsConfiguration("/api/v1/reconciliation/**", configuration);
```

### 6.3 Permission Mapping

All Bank Reconciliation endpoints use `@PreAuthorize("hasAuthority('organizations:write')")` consistent with existing finance endpoints. This means the Finance Operator role in AuthHub needs the `organizations:write` permission — same as current finance users.

---

## 7. Maven Dependencies to Add

Add to [`pom.xml`](pom.xml:56):

```xml
<!-- CSV Parsing (OpenCSV) — LLR-BNK-01.2 -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>

<!-- Excel Parsing (Apache POI) — LLR-BNK-01.2 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>

<!-- Fuzzy String Matching — LLR-BNK-02.2 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.12.0</version>
</dependency>
```

---

## 8. Application Configuration

Add to [`application.yml`](src/main/resources/application.yml:139):

```yaml
bank-reconciliation:
  # Maximum file upload size for bank statements (default: 10 MB)
  max-file-size-bytes: 10485760
  # Allowed file extensions
  allowed-file-types: CSV,XLSX,XLS
  # Matching thresholds
  matching:
    auto-match-threshold: 80       # Score >= 80: auto-match
    suggestion-threshold: 60       # Score 60-79: suggest for review
    # (Score < 60: no action)
  # Batch processing
  batch-size: 200                  # Transactions per matching batch
```

Also ensure the Spring multipart max size supports the file limit:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 15MB
      max-request-size: 20MB
```

---

## 9. Testing Strategy

### 9.1 Unit Tests

| Test Class | What to Test |
|-----------|-------------|
| `CsvStatementParserTest` | CSV parsing with various formats (different delimiters, column orders, missing columns) |
| `ExcelStatementParserTest` | Excel parsing, multi-sheet handling, password-protected files |
| `MatchingScoreCalculatorTest` | All score scenarios: exact match, fuzzy date, description similarity, vendor patterns |
| `BankCategorizationServiceImplTest` | Single/bulk/split categorization with mocked dependencies |
| `BankMatchingServiceImplTest` | Auto-match, suggestion creation, vendor learning |

Follow existing test patterns:
- Mock repositories and services with Mockito
- Use `@ExtendWith(MockitoExtension.class)`
- Test both success paths and exception paths

### 9.2 Integration Tests

| Test | Scope |
|------|-------|
| `BankStatementUploadIntegrationTest` | Full upload → parse → verify transactions flow |
| `BankMatchingIntegrationTest` | Upload statement → run matching → verify auto-matches and suggestions |
| `BankCategorizationIntegrationTest` | Upload → categorize → verify ledger entries |
| `BankStatementControllerTest` | HTTP layer: upload, list, get, parse endpoints |

### 9.3 Test Data

Create sample bank statement files in `src/test/resources/test-data/`:

```
test-data/
├── bank-statements/
│   ├── sample-standard.csv       -- Standard CSV format
│   ├── sample-alternative.csv    -- Different column headers/order
│   ├── sample-tab-delimited.csv  -- Tab-separated
│   ├── sample-semicolon.csv      -- Semicolon-separated
│   ├── sample-single-amount.csv  -- Single amount column (signed)
│   ├── sample-standard.xlsx      -- Excel format
│   └── sample-malformed.csv      -- Intentionally broken for error testing
```

---

## 10. Risk Assessment & Edge Cases

### 10.1 Architectural Risks

| Risk | Mitigation |
|------|-----------|
| **LedgerEntry immutability violated** | Instead of modifying LedgerEntry, reconciliation fields are on ExpenseTransaction — keeping ledger entries append-only |
| **Concurrent categorization** | Use `@Version` (optimistic locking) on BankTransaction entity to prevent double-categorization |
| **Large statement performance** | Batch processing (200 txns/batch) + `@Async` matching job prevents upload endpoint timeout |
| **File parsing memory** | Stream-based CSV parsing (OpenCSV reads line-by-line). For Excel, process in streaming mode (`SXSSFWorkbook` for writing, `XSSFReader` for reading) |
| **Vendor pattern collision** | The `UNIQUE(legal_entity_id, bank_description_pattern)` constraint prevents duplicate patterns per entity |

### 10.2 Edge Cases

| Edge Case | Handling |
|-----------|----------|
| **Empty statement** | Return success with `transactionCount = 0`, don't trigger matching |
| **Statement with only credits (deposits)** | All transactions extracted; matching won't find expenses for deposits (since expenses are debits). These stay UNMATCHED for manual review |
| **Duplicate file content** | Content-hash-based deduplication (future). For now, period+account-based |
| **Bank account with no linked fa_account** | When categorizing, throw clear error: "Bank account X has no linked funding account for double-entry posting" |
| **Transaction amount = 0** | Skip during parsing (zero-value transactions are typically bank fees that net to zero) |
| **Statement period overlap** | Duplicate detection catches exact period match. Partial overlaps allowed (different start/end dates) |
| **Expense in different currency** | Bank statement currency vs entity base currency — for now, assume same currency. Multi-currency reconciliation is a future enhancement |
| **Vendor not found during categorization** | User creates vendor on-the-fly via existing `POST /api/v1/expense/vendors` endpoint before categorizing |

### 10.3 Performance Considerations

| Concern | Strategy |
|---------|----------|
| **Matching N×M complexity** | Pre-load all unreconciled expenses + vendor patterns. Index on `(legal_entity_id, reconciliation_status)` for expenses |
| **Bulk inserts** | Use `saveAll()` with batch size 500 for transaction insertion |
| **Statement list query** | Index on `(bank_account_id, period_start, period_end)` for duplicate check |
| **Unmatched transaction query** | Composite index on `(legal_entity_id, reconciliation_status, transaction_date)` |

### 10.4 Data Integrity

| Constraint | Implementation |
|-----------|---------------|
| **Split amount sum = bank amount** | Validate in service layer with `BigDecimal.compareTo()` |
| **No double-matching** | Optimistic locking on BankTransaction; unique constraint on `(bank_transaction_id, expense_transaction_id)` in `bnk_suggested_matches` |
| **Entity boundary enforcement** | All queries filter by `legal_entity_id`; matching only searches within same entity |
| **Statement immutability after parsing** | Once status = PARSED, file content cannot be changed (only SUPERSEDED by a new upload) |

---

## Appendix A: File Structure Overview (New Files)

```
src/main/java/com/af/novadesk/api/finance/
├── constants/
│   ├── MatchingMethod.java              (NEW)
│   ├── ReconciliationStatus.java        (NEW)
│   ├── StatementStatus.java             (NEW)
│   ├── SuggestionStatus.java            (NEW)
│   └── VendorMappingConfidence.java     (NEW)
├── controller/
│   └── BankReconciliationController.java (NEW)
├── dto/
│   ├── BankStatementDto.java            (NEW)
│   ├── BankStatementPageDto.java        (NEW)
│   ├── BankStatementUploadRequest.java  (NEW)
│   ├── BankTransactionDto.java          (NEW)
│   ├── BankTransactionPageDto.java      (NEW)
│   ├── BulkCategorizeRequest.java       (NEW)
│   ├── CategorizeTransactionRequest.java(NEW)
│   ├── DuplicateStatementWarningDto.java(NEW)
│   ├── MatchingScoreBreakdown.java      (NEW)
│   ├── ResolveSuggestionRequest.java    (NEW)
│   ├── SplitLineItem.java               (NEW)
│   ├── SplitTransactionRequest.java     (NEW)
│   ├── SuggestedMatchDto.java           (NEW)
│   └── SuggestedMatchPageDto.java       (NEW)
├── entity/
│   ├── BankStatement.java               (NEW)
│   ├── BankTransaction.java             (NEW)
│   ├── SuggestedMatch.java              (NEW)
│   └── VendorMapping.java               (NEW)
├── exception/
│   ├── BankTransactionNotFoundException.java (NEW)
│   ├── DuplicateStatementException.java      (NEW)
│   ├── InvalidCategorizationException.java   (NEW)
│   ├── SplitAmountMismatchException.java     (NEW)
│   ├── StatementNotFoundException.java       (NEW)
│   ├── StatementParseException.java          (NEW)
│   ├── TransactionAlreadyMatchedException.java (NEW)
│   └── UnsupportedFileTypeException.java     (NEW)
├── mapper/
│   ├── BankStatementMapper.java         (NEW)
│   └── BankTransactionMapper.java       (NEW)
├── repository/
│   ├── BankStatementRepository.java     (NEW)
│   ├── BankTransactionRepository.java   (NEW)
│   ├── SuggestedMatchRepository.java    (NEW)
│   └── VendorMappingRepository.java     (NEW)
├── service/
│   ├── BankCategorizationService.java   (NEW)
│   ├── BankMatchingService.java         (NEW)
│   ├── BankStatementParser.java         (NEW - interface)
│   ├── BankStatementService.java        (NEW - interface)
│   └── impl/
│       ├── BankCategorizationServiceImpl.java  (NEW)
│       ├── BankMatchingServiceImpl.java        (NEW)
│       ├── BankStatementServiceImpl.java       (NEW)
│       ├── CsvStatementParser.java             (NEW)
│       ├── ExcelStatementParser.java           (NEW)
│       └── MatchingScoreCalculator.java        (NEW)
└── (MODIFIED) entity/
    └── ExpenseTransaction.java          (MODIFIED: add reconciliation fields)

src/main/resources/db/migration/
├── V1.69__create_bnk_statements_table.sql       (NEW)
├── V1.70__create_bnk_transactions_table.sql     (NEW)
├── V1.71__add_reconciliation_to_expenses.sql    (NEW)
├── V1.72__create_bnk_vendor_mappings_table.sql  (NEW)
└── V1.73__create_bnk_suggested_matches_table.sql(NEW)
```

---

## Appendix B: Key Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Reconcile at ExpenseTransaction level, not LedgerEntry** | LedgerEntry is documented as immutable/append-only. ExpenseTransaction is the aggregate root for expenses. |
| 2 | **`bnk_` table prefix** | Follows existing convention (`fa_`, `exp_`, `pr_`, `ast_`). Keeps bank reconciliation tables grouped. |
| 3 | **Same permission (`organizations:write`)** | Finance users already have this permission. Keeps RBAC simple. |
| 4 | **`@Async` matching after upload** | Prevents upload endpoint from blocking on matching (especially for large statements). |
| 5 | **Parser factory pattern** | Allows adding new formats (PDF/OCR) without modifying existing code (Open/Closed Principle). |
| 6 | **VendorMapping per entity** | Bank statement descriptions vary by country/bank. Entity-scoped mappings prevent cross-contamination. |
| 7 | **`amount` signed in BankTransaction** | Positive = credit (money in), Negative = debit (money out). Matches banking convention and simplifies debit-matching against expenses. |
| 8 | **No LedgerEntry FK in migration V1.70** | The FK to `fa_ledger_entries` is added after the matching logic is stable, avoiding migration order issues. The column `matched_ledger_entry_id` is a plain UUID initially. |
