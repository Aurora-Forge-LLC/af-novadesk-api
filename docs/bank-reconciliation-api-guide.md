# Bank Reconciliation API — Frontend Integration Guide

> **Base URL:** `{{baseUrl}}/api/v1/bank-reconciliation/statements`
> **Auth:** Bearer JWT token (Finance role, `organizations:write` permission)
> **Content-Type:** `multipart/form-data` (upload/replace), `application/json` (queries/resolve/categorize)
> **Date Format:** `YYYY-MM-DD` (ISO 8601)

---

## Table of Contents

1. [Quick Reference](#1-quick-reference)
2. [Authentication](#2-authentication)
3. [LLR-BNK-01: Statement Upload & Management](#3-llr-bnk-01-statement-upload--management)
   - [3.1 Upload Statement](#31-upload-statement)
   - [3.2 Replace Statement](#32-replace-statement)
   - [3.3 Check Duplicate (Pre-flight)](#33-check-duplicate-pre-flight)
   - [3.4 Get Statement Detail](#34-get-statement-detail)
   - [3.5 List Statements](#35-list-statements)
   - [3.6 Parse Statement (Re-parse)](#36-parse-statement-re-parse)
4. [LLR-BNK-02: Intelligent Matching](#4-llr-bnk-02-intelligent-matching)
   - [4.1 Get Suggested Matches](#41-get-suggested-matches)
   - [4.2 Resolve Suggestion (Accept/Reject)](#42-resolve-suggestion-acceptreject)
5. [LLR-BNK-03: Manual Transaction Categorization](#5-llr-bnk-03-manual-transaction-categorization)
   - [5.1 Get Unmatched Transactions](#51-get-unmatched-transactions)
   - [5.2 Categorize Single Transaction](#52-categorize-single-transaction)
   - [5.3 Bulk Categorize](#53-bulk-categorize)
   - [5.4 Split Transaction](#54-split-transaction)
6. [Data Models](#6-data-models)
7. [Error Codes](#7-error-codes)
8. [UI Workflow Guide](#8-ui-workflow-guide)
9. [File Formats Supported](#9-file-formats-supported)
10. [Matching Algorithm Reference](#10-matching-algorithm-reference)

---

## 1. Quick Reference

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/upload` | Upload & auto-parse a bank statement |
| `POST` | `/{id}/replace` | Replace existing statement (supersede) |
| `GET` | `/check-duplicate` | Pre-flight duplicate check before upload |
| `GET` | `/{id}` | Get statement detail with transactions |
| `GET` | `/` | Paginated list of statements |
| `POST` | `/{id}/parse` | Re-parse stored statement |
| `GET` | `/suggested-matches` | Pending match suggestions for review |
| `POST` | `/suggested-matches/{id}/resolve` | Accept or reject a suggested match |
| `GET` | `/transactions/unmatched` | Paginated list of unmatched transactions with filters |
| `POST` | `/transactions/{id}/categorize` | Categorize a single unmatched transaction |
| `POST` | `/transactions/bulk-categorize` | Categorize multiple unmatched transactions at once |
| `POST` | `/transactions/{id}/split` | Split a transaction into multiple expenses |

---

## 2. Authentication

All endpoints require a valid JWT token with `organizations:write` permission. Pass it as a Bearer token in the `Authorization` header.

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

---

## 3. LLR-BNK-01: Statement Upload & Management

### 3.1 Upload Statement

Upload a bank statement file. The system validates, stores (MinIO AES-256), parses, extracts transactions, and triggers async matching.

```
POST /api/v1/bank-reconciliation/statements/upload
Content-Type: multipart/form-data
```

**Form Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `entityId` | UUID | ✅ | Legal entity UUID |
| `bankAccountId` | UUID | ✅ | Bank account UUID |
| `periodStart` | String | ✅ | Statement period start (`YYYY-MM-DD`) |
| `periodEnd` | String | ✅ | Statement period end (`YYYY-MM-DD`) |
| `file` | File | ✅ | Bank statement file (CSV, XLSX, XLS, PDF) |
| `filePassword` | String | ❌ | Password for encrypted Excel/PDF files |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

**⚠️ IMPORTANT:** Send all fields as individual `form-data` parameters. Do NOT nest under a JSON key.

**JavaScript/TypeScript Example:**
```typescript
const formData = new FormData();
formData.append('entityId', entityId);
formData.append('bankAccountId', bankAccountId);
formData.append('periodStart', '2026-01-01');
formData.append('periodEnd', '2026-01-31');
formData.append('file', fileInput.files[0]);
formData.append('filePassword', '');  // optional
formData.append('notes', 'January reconciliation');  // optional

const response = await fetch(`${BASE_URL}/upload`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` },
  body: formData
});
```

**Success Response (201):**
```json
{
  "success": true,
  "code": 201,
  "message": "Statement uploaded and parsed successfully",
  "data": {
    "id": "387e6dbd-0496-44f3-a10c-fcba05c0efff",
    "legalEntityId": "e9508036-1c6c-46c9-8142-ed6e47c3abb6",
    "legalEntityName": "Acme Corp",
    "bankAccountId": "3085c218-9b74-4ec2-8f2e-fe91161e998c",
    "bankAccountLabel": "Petty Cash – NP",
    "uploadedByUserId": "4b12e8e8-8e30-4fce-be4a-7938a6b01156",
    "uploadedByName": "John Doe",
    "originalFilename": "statement-jan.csv",
    "fileType": "CSV",
    "fileSizeBytes": 675,
    "isEncrypted": true,
    "periodStart": "2026-01-01",
    "periodEnd": "2026-01-31",
    "transactionCount": 12,
    "notes": "January reconciliation",
    "statementStatus": "PARSED",
    "reconciliationStatus": "UNMATCHED",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z",
    "transactions": [
      {
        "transactionDate": "2026-01-02",
        "description": "Opening Balance",
        "debit": null,
        "credit": 100000.00,
        "balance": 100000.00,
        "signedAmount": 100000.00
      },
      {
        "transactionDate": "2026-01-05",
        "description": "Office Supplies Payment",
        "debit": 1500.50,
        "credit": null,
        "balance": 123499.50,
        "signedAmount": -1500.50
      }
    ]
  },
  "timestamp": "2026-01-15T10:30:00Z"
}
```

**Error Responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | `VALIDATION_ERROR` | Missing required field, invalid file type |
| 400 | `FIN_BNK_004` | Unsupported file type (not CSV/XLSX/XLS/PDF) |
| 409 | `FIN_BNK_002` | Duplicate statement for same account+period |
| 413 | — | File exceeds 10 MB limit |
| 422 | `FIN_BNK_003` | File parsing failed (malformed CSV/Excel, wrong headers). **`details` field contains sample expected format** |

**UI Behavior:**
1. Show loading indicator while uploading
2. On 201: Display "Uploaded successfully — 12 transactions extracted"
3. On 409: Show warning with existing statement info, offer "Replace" or "Cancel" buttons
4. On 422: Show the `details` field (sample CSV format) to help user fix their file

---

### 3.2 Replace Statement

Replace an existing statement. The old statement is marked `SUPERSEDED`, and a new one is created.

```
POST /api/v1/bank-reconciliation/statements/{id}/replace
Content-Type: multipart/form-data
```

**Path Parameter:**

| Param | Type | Description |
|-------|------|-------------|
| `id` | UUID | UUID of the statement to replace |

**Form Fields:** Same as [Upload](#31-upload-statement)

**Response:** Same as Upload (201 with new `BankStatementDto`)

**UI Behavior:**
- Only call this after user confirms replacement on the duplicate warning screen
- The `id` comes from the `existingStatementId` field in the duplicate warning response

---

### 3.3 Check Duplicate (Pre-flight)

Check if a statement already exists for a given bank account + period BEFORE attempting upload. Call this when the user fills out the upload form (on period change or before submit).

```
GET /api/v1/bank-reconciliation/statements/check-duplicate
```

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `bankAccountId` | UUID | ✅ | Bank account UUID |
| `periodStart` | String | ✅ | Period start (`YYYY-MM-DD`) |
| `periodEnd` | String | ✅ | Period end (`YYYY-MM-DD`) |

**Success Response (200 — No duplicate):**
```json
{
  "success": true,
  "code": 200,
  "message": "No duplicate found",
  "data": null
}
```

**Success Response (200 — Duplicate found):**
```json
{
  "success": true,
  "code": 200,
  "message": "Duplicate statement found",
  "data": {
    "existingStatementId": "abc123...",
    "existingUploadedAt": "2026-01-10T08:00:00Z",
    "existingUploadedBy": "John Doe",
    "existingFilename": "statement-jan.csv",
    "periodStart": "2026-01-01",
    "periodEnd": "2026-01-31",
    "suggestedAction": "REPLACE"
  }
}
```

**UI Behavior:**
```
┌─────────────────────────────────────────┐
│ ⚠️ Duplicate Statement Detected         │
│                                         │
│ A statement for this account and period │
│ already exists:                         │
│ • Uploaded by: John Doe                 │
│ • On: January 10, 2026                  │
│ • File: statement-jan.csv               │
│                                         │
│ [Replace]  [Cancel]                     │
└─────────────────────────────────────────┘
```
- **Replace:** Call `POST /{existingStatementId}/replace` with the new file
- **Cancel:** Close the warning, let user change period or go back

---

### 3.4 Get Statement Detail

Retrieve a previously uploaded statement with all its parsed transactions.

```
GET /api/v1/bank-reconciliation/statements/{id}
```

**Path Parameter:** `id` — Statement UUID

**Response:** 200 with `BankStatementDto` (see [Upload response](#31-upload-statement))

---

### 3.5 List Statements

Paginated list of statements for a legal entity.

```
GET /api/v1/bank-reconciliation/statements?entityId={uuid}&page=0&size=20
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `entityId` | UUID | (required) | Legal entity UUID |
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Items per page |

**Response:**
```json
{
  "success": true,
  "code": 200,
  "data": {
    "content": [ /* Array of BankStatementDto (without transactions array) */ ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
}
```

**Note:** The list view does NOT include the `transactions` array for performance. Use `GET /{id}` to get transactions for a specific statement.

---

### 3.6 Parse Statement (Re-parse)

Re-parse a previously uploaded statement that is in `UPLOADED` or `FAILED` status. This is typically not needed in the normal flow (upload auto-parses), but useful for retry after a failed parse.

```
POST /api/v1/bank-reconciliation/statements/{id}/parse
```

**Response:** 200 with updated `BankStatementDto`

---

## 4. LLR-BNK-02: Intelligent Matching

When a statement is uploaded, the system **automatically** runs matching in the background (`@Async`). The matching compares each bank debit transaction against unreconciled expenses in the same legal entity. Based on the score:

| Score | Action | Result |
|-------|--------|--------|
| ≥ 80 | Auto-match | Both records updated automatically. No user action needed. |
| 60–79 | Suggest | Suggested match created. **User must review in the Suggestions Queue.** |
| < 60 | Skip | No action. User must manually categorize later. |

### 4.1 Get Suggested Matches

Retrieve the list of pending match suggestions that require user review.

```
GET /api/v1/bank-reconciliation/statements/suggested-matches?page=0&size=20
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | `0` | Zero-based page number |
| `size` | int | `20` | Items per page |

**Response:**
```json
{
  "success": true,
  "code": 200,
  "data": {
    "content": [
      {
        "id": "suggestion-uuid-here",
        "bankTransaction": {
          "id": "btxn-uuid",
          "transactionDate": "2026-01-07",
          "description": "Utility Bill - Electricity",
          "amount": -3200.75,
          "balance": 120298.75
        },
        "suggestedExpense": {
          "id": "expense-uuid",
          "expenseDate": "2026-01-09",
          "amount": 3200.75,
          "vendorName": "Utility Provider Inc",
          "currencyCode": "USD"
        },
        "matchingScore": 60,
        "scoreBreakdown": "{\"totalScore\":60,\"amountScore\":50,\"dateScore\":10,\"descriptionScore\":0,\"tier\":\"SUGGEST\"}",
        "suggestedBy": "SYSTEM",
        "suggestionStatus": "PENDING",
        "createdAt": "2026-01-15T10:30:05Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

**UI Behavior:**

Show a reviewable queue. Each item should display:

```
┌──────────────────────────────────────────────────────────┐
│ 🔍 Suggested Match — Score: 60                          │
│                                                          │
│ Bank Transaction                    Suggested Expense    │
│ ─────────────────                   ─────────────────    │
│ Date: Jan 7, 2026                   Date: Jan 9, 2026    │
│ Desc: Utility Bill - Electricity    Vendor: Utility Prov │
│ Amount: -$3,200.75                  Amount: $3,200.75    │
│                                                          │
│ Score Breakdown:                                         │
│ ██████████ Amount: 50/50  (exact match)                  │
│ ████░░░░░░ Date: 10/30    (2 days apart)                │
│ ░░░░░░░░░░ Description: 0/20 (no vendor match)          │
│                                                          │
│ [✓ Accept]  [✗ Reject]  [🔍 Find Different]              │
└──────────────────────────────────────────────────────────┘
```

Score colors:
- Green: 80–100 (this card shouldn't appear — auto-matched)
- **Orange: 60–79** (suggested — needs review)
- Red: 0–59 (skipped)

---

### 4.2 Resolve Suggestion (Accept/Reject)

Accept or reject a suggested match.

```
POST /api/v1/bank-reconciliation/statements/suggested-matches/{id}/resolve
Content-Type: application/json
```

**Path Parameter:** `id` — Suggested match UUID

**Request Body:**
```json
{
  "action": "ACCEPT"
}
```
or
```json
{
  "action": "REJECT"
}
```

**Response (200 — Accepted):**
```json
{
  "success": true,
  "code": 200,
  "message": "Suggestion resolved successfully",
  "data": {
    "id": "suggestion-uuid",
    "suggestionStatus": "ACCEPTED"
  }
}
```

**What happens:**

| Action | Bank Transaction | Expense Transaction | Suggestion |
|--------|-----------------|---------------------|------------|
| `ACCEPT` | `reconciliationStatus → MATCHED`, `matchingMethod → USER_CONFIRMED` | `reconciliationStatus → RECONCILED`, `matchedBankTransactionId` set | `suggestionStatus → ACCEPTED` |
| `REJECT` | `reconciliationStatus → UNMATCHED` (reset) | Unchanged | `suggestionStatus → REJECTED` |

Additionally, on ACCEPT: the system **learns** the vendor pattern. Example: `"Utility Bill - Electricity"` → pattern `"UTILITY%"` → maps to vendor "Utility Provider Inc". Future bank transactions with "UTILITY" in description will get full 20 description points.

---

## 5. LLR-BNK-03: Manual Transaction Categorization

When a bank transaction cannot be auto-matched (score < 60), it appears in the **Unmatched Transactions** queue. A finance operator must manually categorize it by selecting a vendor, expense category, and optionally department/project. The system creates an expense transaction with proper double-entry ledger entries.

**What happens on categorization:**
- Creates an `ExpenseTransaction` (POSTED status, RECONCILED)
- Creates two `LedgerEntry` records forming a double-entry:
  - **CREDIT** → Source bank account (money out of bank)
  - **DEBIT** → Selected expense category (chart of account)
- Links the expense to the bank transaction via `matchedBankTransactionId`
- Marks the bank transaction as `MATCHED` (score: 100, method: MANUAL)
- Learns the vendor pattern for future auto-matching

**Optimistic Locking:** The `BankTransaction` entity uses `@Version`. If two operators try to categorize the same transaction concurrently, the second will receive a `409 Conflict` error.

---

### 5.1 Get Unmatched Transactions

Paginated list of unmatched bank transactions with extensive filtering and sorting.

```
GET /api/v1/bank-reconciliation/statements/transactions/unmatched
  ?entityId={uuid}
  &bankAccountId={uuid}     (optional)
  &dateFrom=2026-01-01      (optional)
  &dateTo=2026-01-31        (optional)
  &amountMin=100            (optional)
  &amountMax=10000          (optional)
  &search=electricity       (optional, case-insensitive)
  &page=0
  &size=50
  &sortBy=transactionDate
  &sortDir=DESC
```

**Query Parameters:**

| Param | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `entityId` | UUID | ✅ | — | Legal entity UUID |
| `bankAccountId` | UUID | ❌ | — | Filter by bank account |
| `dateFrom` | String (date) | ❌ | — | Filter transactions from this date |
| `dateTo` | String (date) | ❌ | — | Filter transactions up to this date |
| `amountMin` | Number | ❌ | — | Minimum absolute amount (inclusive) |
| `amountMax` | Number | ❌ | — | Maximum absolute amount (inclusive) |
| `search` | String | ❌ | — | Case-insensitive description search |
| `page` | int | ❌ | `0` | Zero-based page number |
| `size` | int | ❌ | `50` | Items per page |
| `sortBy` | String | ❌ | `transactionDate` | Sort field: `transactionDate` or `amount` |
| `sortDir` | String | ❌ | `DESC` | Sort direction: `ASC` or `DESC` |

**Response (200):**
```json
{
  "success": true,
  "code": 200,
  "message": "Unmatched transactions retrieved successfully",
  "data": {
    "content": [
      {
        "id": "btxn-uuid-1",
        "transactionDate": "2026-01-15",
        "description": "Office Supplies - Staples",
        "amount": -450.75,
        "balance": 99549.25,
        "reconciliationStatus": "UNMATCHED",
        "bankAccountLabel": "Operating Account – NP",
        "matchingScore": null,
        "matchingMethod": null
      },
      {
        "id": "btxn-uuid-2",
        "transactionDate": "2026-01-12",
        "description": "Internet Service Payment",
        "amount": -89.99,
        "balance": 99539.26,
        "reconciliationStatus": "UNMATCHED",
        "bankAccountLabel": "Operating Account – NP",
        "matchingScore": null,
        "matchingMethod": null
      }
    ],
    "page": 0,
    "size": 50,
    "totalElements": 15,
    "totalPages": 1
  }
}
```

**UI Behavior:**

```
┌──────────────────────────────────────────────────────────────────┐
│ Unmatched Transactions (15)                          [Filters ▾] │
│                                                                  │
│ ☐ Date       │ Description              │ Amount    │ Actions   │
│ ────────────┼─────────────────────────┼──────────┼──────────│
│ ☐ Jan 15    │ Office Supplies - Staples │ -$450.75  │ [Categorize] [Split] │
│ ☐ Jan 12    │ Internet Service Payment  │ -$89.99   │ [Categorize] [Split] │
│ ☐ Jan 08    │ Rent Payment - Jan 2026   │ -$5,000.00│ [Categorize] [Split] │
│                                                                  │
│ [Bulk Categorize Selected]                                       │
│                                                                  │
│ ◀ Page 1 of 1 ▶                                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

### 5.2 Categorize Single Transaction

Categorize one unmatched bank transaction by creating an expense with double-entry ledger entries.

```
POST /api/v1/bank-reconciliation/statements/transactions/{id}/categorize
Content-Type: application/json
```

**Path Parameter:** `id` — Bank transaction UUID

**Request Body:**
```json
{
  "vendorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "chartOfAccountId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
  "department": "IT",
  "project": "PROJ-2026-01",
  "notes": "Monthly internet subscription"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vendorId` | UUID | ✅ | Vendor/Payee UUID |
| `chartOfAccountId` | UUID | ✅ | Expense category (chart of account) UUID |
| `department` | String | ❌ | Department name (appended to description: `[Dept: IT]`) |
| `project` | String | ❌ | Project code (appended to description: `[Project: PROJ-2026-01]`) |
| `notes` | String | ❌ | Optional notes (max 500 chars). Falls back to bank description if blank. |

**Validation:**
- Bank transaction must be `UNMATCHED` (409 if already categorized)
- Chart of account must belong to the same legal entity as the bank transaction (400 if mismatch)
- `vendorId` and `chartOfAccountId` must exist (400 if not found)
- Optimistic locking prevents concurrent categorization of the same transaction (409)

**Response (201):**
```json
{
  "success": true,
  "code": 201,
  "message": "Transaction categorized successfully",
  "data": {
    "id": "expense-uuid-here",
    "legalEntityId": "entity-uuid",
    "vendorId": "vendor-uuid",
    "expenseDate": "2026-01-12",
    "amount": 89.99,
    "currencyCode": "USD",
    "paymentMethod": "BANK_TRANSFER",
    "description": "Monthly internet subscription [Dept: IT] [Project: PROJ-2026-01]",
    "transactionStatus": "POSTED",
    "createdAt": "2026-01-15T11:00:00Z",
    "sourceAccountId": "account-uuid",
    "chartOfAccountId": "coa-uuid"
  }
}
```

**Error Responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | — | Vendor or COA not found, or COA belongs to different entity |
| 404 | — | Bank transaction not found |
| 409 | — | Bank transaction is not UNMATCHED (already categorized) or concurrent update conflict |

---

### 5.3 Bulk Categorize

Apply the same vendor and expense category to multiple unmatched transactions at once. Useful for recurring expenses.

```
POST /api/v1/bank-reconciliation/statements/transactions/bulk-categorize
Content-Type: application/json
```

**Request Body:**
```json
{
  "transactionIds": [
    "btxn-uuid-1",
    "btxn-uuid-2",
    "btxn-uuid-3"
  ],
  "vendorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "chartOfAccountId": "4fa85f64-5717-4562-b3fc-2c963f66afa7",
  "notes": "Monthly internet charges"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `transactionIds` | UUID[] | ✅ | List of bank transaction UUIDs (1–100) |
| `vendorId` | UUID | ✅ | Vendor/Payee UUID |
| `chartOfAccountId` | UUID | ✅ | Expense category UUID |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

**Behavior:**
- Processes each transaction individually
- **Continue-on-error:** If one transaction fails (e.g., already categorized), the rest still proceed
- Check the response array size vs `transactionIds` size to identify failures

**Response (201):**
```json
{
  "success": true,
  "code": 201,
  "message": "2 transactions categorized successfully",
  "data": [
    { /* ExpenseTransactionDto for txn 1 */ },
    { /* ExpenseTransactionDto for txn 3 */ }
  ]
}
```

**Note:** In this example, txn 2 failed (e.g., already categorized), so only 2 of 3 succeeded. The response message indicates the actual success count.

---

### 5.4 Split Transaction

Split a single bank transaction into multiple expense transactions — for example, when a ₹5,000 bank payment covers both Office Supplies (₹4,000) and Utilities (₹1,000).

```
POST /api/v1/bank-reconciliation/statements/transactions/{id}/split
Content-Type: application/json
```

**Path Parameter:** `id` — Bank transaction UUID

**Request Body:**
```json
{
  "lines": [
    {
      "vendorId": "vendor-office-supplies-uuid",
      "chartOfAccountId": "coa-office-supplies-uuid",
      "amount": 4000.00
    },
    {
      "vendorId": "vendor-utilities-uuid",
      "chartOfAccountId": "coa-utilities-uuid",
      "amount": 1000.00
    }
  ],
  "notes": "Split: Office supplies + January utilities"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `lines` | Array | ✅ | 2–20 split line items |
| `lines[].vendorId` | UUID | ✅ | Vendor UUID for this line |
| `lines[].chartOfAccountId` | UUID | ✅ | Expense category UUID for this line |
| `lines[].amount` | Number | ✅ | Split amount (min 0.01) |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

**Critical Validation:**
- `SUM(lines[*].amount)` must equal `ABS(bank_transaction_amount)` within **0.001 tolerance**
- If amounts don't match: **400 error** with details showing the difference
- Each COA must belong to the same legal entity as the bank transaction

**Response (201):**
```json
{
  "success": true,
  "code": 201,
  "message": "2 expense transactions created from split",
  "data": [
    {
      "id": "expense-uuid-1",
      "legalEntityId": "entity-uuid",
      "vendorId": "vendor-office-supplies-uuid",
      "expenseDate": "2026-01-10",
      "amount": 4000.00,
      "currencyCode": "USD",
      "paymentMethod": "BANK_TRANSFER",
      "description": "Split: Office supplies + January utilities",
      "transactionStatus": "POSTED",
      "createdAt": "2026-01-15T11:00:00Z"
    },
    {
      "id": "expense-uuid-2",
      "legalEntityId": "entity-uuid",
      "vendorId": "vendor-utilities-uuid",
      "expenseDate": "2026-01-10",
      "amount": 1000.00,
      "currencyCode": "USD",
      "paymentMethod": "BANK_TRANSFER",
      "description": "Split: Office supplies + January utilities",
      "transactionStatus": "POSTED",
      "createdAt": "2026-01-15T11:00:00Z"
    }
  ]
}
```

**Error Responses:**

| Status | Code | Condition |
|--------|------|-----------|
| 400 | — | Split amounts don't sum to bank transaction amount, or fewer than 2 lines |
| 400 | — | Vendor/COA not found, or COA belongs to different entity |
| 404 | — | Bank transaction not found |
| 409 | — | Bank transaction is not UNMATCHED or concurrent update conflict |

**UI Behavior:**

```
┌──────────────────────────────────────────────────────────┐
│ Split Transaction: ₹5,000.00                             │
│                                                          │
│ Bank Txn: Rent & Utilities Payment                       │
│ Date: Jan 10, 2026                                      │
│                                                          │
│ Split Lines:                                            │
│ ┌──────────┬────────────────┬──────────┬──────────┐    │
│ │ Vendor   │ Category       │ Amount   │          │    │
│ ├──────────┼────────────────┼──────────┼──────────┤    │
│ │ ACME Inc │ Office Supplies│ 4000.00  │ [Remove] │    │
│ │ PowerCo  │ Utilities      │ 1000.00  │ [Remove] │    │
│ └──────────┴────────────────┴──────────┴──────────┘    │
│                                  Sum: ₹5,000.00 ✔       │
│                                                          │
│ [+ Add Line]                                             │
│                                                          │
│ Notes: [Split: Office supplies + January utilities  ]    │
│                                                          │
│ [Submit Split]  [Cancel]                                │
└──────────────────────────────────────────────────────────┘
```

---

## 6. Data Models

### BankStatementDto

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Statement identifier |
| `legalEntityId` | UUID | Legal entity UUID |
| `legalEntityName` | String | Legal entity display name |
| `bankAccountId` | UUID | Bank account UUID |
| `bankAccountLabel` | String | Bank account display label |
| `uploadedByUserId` | UUID | Uploader's user UUID |
| `uploadedByName` | String | Uploader's display name |
| `originalFilename` | String | Original file name |
| `fileType` | String | `CSV`, `XLSX`, `XLS`, or `PDF` |
| `fileSizeBytes` | Integer | File size in bytes |
| `isEncrypted` | Boolean | Whether stored with AES-256 encryption |
| `periodStart` | String (date) | Statement period start |
| `periodEnd` | String (date) | Statement period end |
| `transactionCount` | Integer | Number of transactions extracted |
| `notes` | String | User notes (max 500) |
| `statementStatus` | String | `UPLOADED`, `PARSED`, `SUPERSEDED`, `FAILED` |
| `reconciliationStatus` | String | `UNMATCHED`, `SUGGESTED`, `MATCHED`, `IGNORED` |
| `createdAt` | DateTime | Upload timestamp |
| `updatedAt` | DateTime | Last update timestamp |
| `transactions` | Array of BankTransactionDto | Parsed transactions (detail/upload views only) |

### BankTransactionDto

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Transaction identifier |
| `transactionDate` | String (date) | Date of transaction |
| `description` | String | Transaction description/narration |
| `debit` | Number (nullable) | Money out. Null if credit-only row. |
| `credit` | Number (nullable) | Money in. Null if debit-only row. |
| `balance` | Number (nullable) | Running balance after transaction |
| `signedAmount` | Number | Positive = credit, Negative = debit |
| `reconciliationStatus` | String | `UNMATCHED`, `MATCHED`, `IGNORED` |
| `bankAccountLabel` | String | Bank account display label |
| `matchingScore` | Number (nullable) | 0–100 when matched |
| `matchingMethod` | String (nullable) | `AUTO`, `MANUAL`, `USER_CONFIRMED` |

**Important:** `signedAmount` is the internal representation used for matching. Negative values (debits/money out) are matched against expenses.

### SuggestedMatchDto

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Suggestion identifier |
| `bankTransaction` | Object `{id, transactionDate, description, amount, balance}` | Bank transaction summary |
| `suggestedExpense` | Object `{id, expenseDate, amount, vendorName, currencyCode}` | Matching expense summary |
| `matchingScore` | Number | Score 60–79 |
| `scoreBreakdown` | String (JSON) | Full breakdown: `{"totalScore":60,"amountScore":50,"dateScore":10,"descriptionScore":0,"tier":"SUGGEST"}` |
| `suggestedBy` | String | Always `"SYSTEM"` for auto-generated suggestions |
| `suggestionStatus` | String | `PENDING`, `ACCEPTED`, `REJECTED` |
| `createdAt` | DateTime | When suggestion was created |

### DuplicateStatementWarningDto

| Field | Type | Description |
|-------|------|-------------|
| `existingStatementId` | UUID | ID of existing statement to replace |
| `existingUploadedAt` | DateTime | When it was uploaded |
| `existingUploadedBy` | String | Display name of uploader |
| `existingFilename` | String | Original filename |
| `periodStart` | String (date) | Duplicate period start |
| `periodEnd` | String (date) | Duplicate period end |
| `suggestedAction` | String | Always `"REPLACE"` |

### CategorizeTransactionRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vendorId` | UUID | ✅ | Vendor/Payee UUID |
| `chartOfAccountId` | UUID | ✅ | Expense category UUID |
| `department` | String | ❌ | Department name (appended to description) |
| `project` | String | ❌ | Project code (appended to description) |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

### BulkCategorizeRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `transactionIds` | UUID[] | ✅ | 1–100 bank transaction UUIDs |
| `vendorId` | UUID | ✅ | Shared vendor UUID |
| `chartOfAccountId` | UUID | ✅ | Shared expense category UUID |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

### SplitLineItem

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `vendorId` | UUID | ✅ | Vendor UUID for this line |
| `chartOfAccountId` | UUID | ✅ | Expense category UUID for this line |
| `amount` | Number | ✅ | Split amount (min 0.01) |

### SplitTransactionRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `lines` | SplitLineItem[] | ✅ | 2–20 split line items |
| `notes` | String | ❌ | Optional notes (max 500 chars) |

### BankTransactionPageDto

| Field | Type | Description |
|-------|------|-------------|
| `content` | BankTransactionDto[] | Array of bank transactions |
| `page` | int | Current page number |
| `size` | int | Page size |
| `totalElements` | int | Total matching records |
| `totalPages` | int | Total pages |

### ExpenseTransactionDto (Response)

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Created expense transaction identifier |
| `legalEntityId` | UUID | Legal entity UUID |
| `vendorId` | UUID | Vendor UUID |
| `expenseDate` | String (date) | Date from bank transaction |
| `amount` | Number | Expense amount (absolute) |
| `currencyCode` | String | Base currency code |
| `paymentMethod` | String | Always `"BANK_TRANSFER"` |
| `description` | String | Notes + optional `[Dept: X] [Project: Y]` |
| `transactionStatus` | String | Always `"POSTED"` |
| `createdAt` | DateTime | Creation timestamp |
| `sourceAccountId` | UUID | Source bank fa_account UUID |
| `chartOfAccountId` | UUID | Expense category UUID |

### Error Response

```json
{
  "success": false,
  "errorCode": "FIN_BNK_003",
  "message": "Failed to parse bank statement: Could not identify a date column...",
  "details": "Expected CSV format with headers:\n  Date,Description,Debit,Credit,Balance\n\nExample:\n  2026-01-02,Opening Balance,,100000.00,100000.00\n  ...",
  "path": "/novadesk-api/api/v1/bank-reconciliation/statements/upload",
  "timestamp": "2026-01-15T10:30:00Z"
}
```

---

## 7. Error Codes

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `VALIDATION_ERROR` | Missing/invalid form fields or request body |
| 400 | `FIN_BNK_004` | File type not supported (must be CSV, XLSX, XLS, or PDF) |
| 400 | — | Split amounts do not sum to bank transaction amount |
| 400 | — | Chart of account belongs to a different legal entity |
| 401 | `FIN_AUTH_001` | Missing or invalid JWT token |
| 403 | `FIN_AUTH_002` | User lacks `organizations:write` permission |
| 404 | `FIN_BNK_001` | Statement not found |
| 404 | — | Bank transaction not found |
| 409 | `FIN_BNK_002` | Duplicate statement for same account+period |
| 409 | — | Bank transaction is not UNMATCHED or concurrent update conflict |
| 413 | — | File exceeds 10 MB |
| 422 | `FIN_BNK_003` | File parsing failed. Check `details` for expected format. |

---

## 8. UI Workflow Guide

### 8.1 Upload Flow

```
User navigates to "Accounting" → "Bank Reconciliation" → "Upload Statement"
    │
    ├─ Form: Entity dropdown, Bank Account dropdown, Period (start/end),
    │         File upload, File Password (optional), Notes (optional)
    │
    ├─ On period change or before submit:
    │    GET /check-duplicate?bankAccountId=...&periodStart=...&periodEnd=...
    │
    │    ├─ data == null → Proceed with upload
    │    └─ data != null → Show duplicate warning
    │         ├─ [Replace] → POST /{existingStatementId}/replace
    │         └─ [Cancel]  → Close warning
    │
    ├─ [Submit] → POST /upload (multipart/form-data)
    │
    │    ├─ 201 → Success! Show "12 transactions extracted. Matching in progress..."
    │    ├─ 409 → Duplicate (if check-duplicate wasn't called first)
    │    └─ 422 → Parse error → Show the `details` field (expected format)
    │
    └─ After upload, matching runs automatically in background.
       User can view results later in "Suggested Matches" queue.
```

### 8.2 Reconciliation Review Flow

```
User navigates to "Accounting" → "Bank Reconciliation" → "Review Matches"
    │
    ├─ GET /suggested-matches?page=0&size=20
    │
    ├─ For each suggestion:
    │    ├─ Review bank transaction vs expense
    │    ├─ [✓ Accept] → POST /suggested-matches/{id}/resolve {"action":"ACCEPT"}
    │    │     → Both records updated. Suggestion marked ACCEPTED.
    │    │     → System learns vendor pattern for future auto-matching.
    │    │
    │    ├─ [✗ Reject] → POST /suggested-matches/{id}/resolve {"action":"REJECT"}
    │    │     → Bank transaction reset to UNMATCHED for manual categorization.
    │    │
    │    └─ [🔍 Find Different] → Open manual search dialog.
    │         → This is a frontend-only action (no dedicated API).
    │         → Use existing expense search/list APIs to find the correct match.
```

### 8.3 Manual Categorization Flow (NEW)

```
User navigates to "Bank Reconciliation" → "Unmatched Transactions"
    │
    ├─ GET /transactions/unmatched?entityId=...&page=0&size=50
    │
    ├─ Filter bar:
    │    Date range | Bank Account dropdown | Amount range | Search box
    │
    ├─ [Sort by Date ▼] [Sort by Amount ▼]
    │
    ├─ For a single transaction:
    │    ├─ [Categorize] → Open categorization form
    │    │   ├─ Pre-filled: Date, Description, Amount (read-only)
    │    │   ├─ Vendor/Payee: Autocomplete dropdown
    │    │   ├─ Expense Category: Dropdown from chart of accounts
    │    │   ├─ Department: Optional text/dropdown
    │    │   ├─ Project: Optional text/dropdown
    │    │   ├─ Notes: Optional text (max 500)
    │    │   └─ [Submit] → POST /transactions/{id}/categorize
    │    │       ├─ 201 → "Transaction categorized successfully"
    │    │       ├─ 409 → "Transaction already categorized by another user"
    │    │       └─ 400 → Validation error (show message)
    │    │
    │    └─ [Split] → Open split form
    │        ├─ Shows bank transaction details (date, desc, amount)
    │        ├─ Add multiple rows: Vendor, Category, Amount
    │        ├─ Real-time validation: running sum vs bank amount
    │        ├─ [Submit Split] → POST /transactions/{id}/split
    │        │   ├─ 201 → "N expense transactions created from split"
    │        │   ├─ 400 → "Split amounts (X) do not match transaction amount (Y)"
    │        │   └─ 409 → Already categorized
    │        └─ [Add Line] to add more rows (max 20)
    │
    ├─ Bulk selection:
    │    ├─ Check multiple transactions
    │    ├─ [Bulk Categorize] → Open bulk form
    │    │   ├─ Select Vendor, Category, Notes
    │    │   └─ [Submit] → POST /transactions/bulk-categorize
    │    │       └─ 201 → "X of Y transactions categorized successfully"
    │    │           → Show which failed (if any) and why
    │
    └─ After categorization, transaction disappears from unmatched queue.
```

### 8.4 Statement Management Flow

```
User navigates to "Accounting" → "Bank Reconciliation" → "Statements"
    │
    ├─ GET /?entityId=...&page=0&size=20
    │
    ├─ Click on a statement row
    │    GET /{id} → Show detail with all transactions
    │
    └─ Statement status indicators:
         UPLOADED → Pending parsing
         PARSED  → Parsed, ready for matching
         FAILED  → Parse failed; use "Re-parse" button
         SUPERSEDED → Replaced by a newer upload
```

---

## 9. File Formats Supported

| Format | Extension | Features |
|--------|-----------|----------|
| **CSV** | `.csv` | Auto-detects delimiter (comma, tab, semicolon, pipe). Auto-detects column headers (date/description/debit/credit/balance — 8+ name variants each). Handles 12 date formats. Supports signed single-amount columns. |
| **Excel** | `.xlsx`, `.xls` | Password-protected workbooks. Formula evaluation. Date-formatted cells. Same column auto-detection as CSV. |
| **PDF** | `.pdf` | Text-based PDF extraction. Password-protected files. Continuation-line merging. Clear error for scanned/image-based PDFs (OCR not supported). |

### Expected CSV Format

The system auto-detects columns, but the standard format is:

```csv
Date,Description,Debit,Credit,Balance
2026-01-02,Opening Balance,,100000.00,100000.00
2026-01-05,Office Supplies Payment,1500.50,,123499.50
```

Alternative headers that work: `Transaction Date`, `Narration`, `Withdrawal`, `Deposit`, `Closing Balance`, etc.

### Test Files

Test CSV files are available at `test-data/bank-statements/`:
- `sample-standard.csv` — Standard comma-delimited
- `sample-alternative-headers.csv` — Different column names
- `sample-tab-delimited.csv` — Tab-separated
- `sample-semicolon-delimited.csv` — Semicolon-separated
- `sample-single-amount.csv` — Single signed amount column
- `sample-date-format-ddmmyyyy.csv` — `dd/MM/yyyy` dates
- `sample-malformed.csv` — Wrong headers (triggers 422 error)

---

## 10. Matching Algorithm Reference

The system uses a **weighted multi-factor scoring algorithm** that runs automatically after upload.

```
Score = Amount (0 or 50) + Date (0/10/20/30) + Description (0/5/10/15/20)

1. Amount: ABS(bank debit) == expense amount → +50 (exact only, all-or-nothing)
2. Date: Same day → +30 | Within ±1 day → +20 | Within ±3 days → +10 | Otherwise → 0
3. Description:
   - Known vendor pattern (learned) → +20
   - Vendor name tokens in description → +15
   - Levenshtein distance < 3 → +10
   - Levenshtein distance < 5 → +5

Decision:
  Score ≥ 80 → AUTO_MATCH (automatic, no user action)
  60 ≤ Score ≤ 79 → SUGGEST (appears in Suggested Matches queue)
  Score < 60 → MANUAL_REVIEW (skipped)
```

### Vendor Learning

Every time a match is accepted (auto or manual), the system learns:
1. Extracts first alpha token from bank description (e.g., `"AMZN MKTP US*1234"` → `"AMZN%"`)
2. Stores mapping: `bank_description_pattern → vendor_id`
3. After 3+ matches for the same pattern → confidence upgrades to `AUTO_LEARNED`
4. Future transactions matching this pattern get full +20 description points

This means the system gets **better over time** — more uploads → more patterns learned → higher auto-match rate.

---

*Document version: 1.1 | Last updated: 2026-06-11*
