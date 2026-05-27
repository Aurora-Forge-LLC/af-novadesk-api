# NovaDesk API — Finance Module Endpoints

> **Base URL:** `{{baseUrl}}/novadesk-api`  
> **Auth:** All endpoints require a valid JWT bearer token (`Authorization: Bearer <token>`).  
> **Response Envelope:** Every response is wrapped in the standard `ApiResponse` envelope.

---

## Table of Contents

1. [Legal Entities](#1-legal-entities)
   - 1.1 [Create Entity](#11-create-entity)
   - 1.2 [List Entities](#12-list-entities)
   - 1.3 [Get Entity by ID](#13-get-entity-by-id)
   - 1.4 [Update Entity Status](#14-update-entity-status)
   - 1.5 [Approve Entity](#15-approve-entity)
   - 1.6 [Reject Entity](#16-reject-entity)
   - 1.7 [List Accessible Entities](#17-list-accessible-entities)
   - 1.8 [List Access Grants](#18-list-access-grants)
   - 1.9 [Grant Access](#19-grant-access)
   - 1.10 [Update Access Role](#110-update-access-role)
   - 1.11 [Revoke Access](#111-revoke-access)
   - 1.12 [Select Entity Context](#112-select-entity-context)
2. [Capital Injection / Funding](#2-capital-injection--funding)
   - 2.1 [Create Capital Injection](#21-create-capital-injection)
   - 2.2 [List Capital Injections](#22-list-capital-injections)
   - 2.3 [Get Capital Injection Detail](#23-get-capital-injection-detail)
   - 2.4 [Update Capital Injection Status](#24-update-capital-injection-status)
   - 2.5 [Get Inter-Entity Transfer Detail](#25-get-inter-entity-transfer-detail)
3. [Exchange Rates](#3-exchange-rates)
   - 3.1 [List Exchange Rates](#31-list-exchange-rates)
   - 3.2 [Get Exchange Rate by ID](#32-get-exchange-rate-by-id)
   - 3.3 [Create Exchange Rate](#33-create-exchange-rate)
   - 3.4 [Update Exchange Rate](#34-update-exchange-rate)
   - 3.5 [Approve Exchange Rate](#35-approve-exchange-rate)
   - 3.6 [Delete Exchange Rate](#36-delete-exchange-rate)
   - 3.7 [CSV Upload Exchange Rates](#37-csv-upload-exchange-rates)
   - 3.8 [Daily Exchange Rate Sync Scheduler](#38-daily-exchange-rate-sync-scheduler)
4. [Funding Accounts](#4-funding-accounts)
   - 4.1 [List Accounts](#41-list-accounts)
   - 4.2 [Get Account by ID](#42-get-account-by-id)
5. [Financial Reports](#5-financial-reports)
   - 5.1 [Ledger Report](#51-ledger-report)
   - 5.2 [Consolidated Report](#52-consolidated-report)
6. [Vendors](#6-vendors)
   - 6.1 [Create Vendor](#61-create-vendor)
   - 6.2 [List Vendors](#62-list-vendors)
   - 6.3 [Get Vendor by ID](#63-get-vendor-by-id)
   - 6.4 [Update Vendor](#64-update-vendor)
   - 6.5 [Update Vendor Status](#65-update-vendor-status)
7. [Expense Transactions](#7-expense-transactions)
   - 7.1 [Record Expense](#71-record-expense)
   - 7.2 [List Expenses](#72-list-expenses)
   - 7.3 [Get Expense by ID](#73-get-expense-by-id)
   - 7.4 [Void Expense](#74-void-expense)
   - 7.5 [Upload Attachment](#75-upload-attachment)
   - 7.6 [List Attachments](#76-list-attachments)
   - 7.7 [Delete Attachment](#77-delete-attachment)
8. [Common Error Response Shapes](#8-common-error-response-shapes)

---

## 1. Legal Entities

**Base path:** `/api/v1/legal-entities`

### 1.1 Create Entity

Creates a new legal entity in `PENDING` approval state. The entity's base currency is auto-derived from the selected country.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "entityName": "Test Entity",
  "entityCode": "TEST-01",
  "country": "US",
  "taxId": "12-3456789",
  "incorporationDate": "2020-01-15"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `entityName` | string | ✅ | max 100 chars | Legal name of the entity |
| `entityCode` | string | ✅ | 2–10 chars, uppercase alphanumeric + hyphens (`^[A-Z0-9-]+$`) | Short unique identifier |
| `country` | enum | ✅ | `US`, `IN`, `NP` | Country of incorporation |
| `taxId` | string | ❌ | max 50 chars | Tax registration number |
| `incorporationDate` | date | ✅ | Past or present | Incorporation date |

### 1.2 List Entities

Lists all entities within the caller's organization (paginated).

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | int | ❌ | `0` | Zero-based page index |
| `size` | int | ❌ | `20` | Page size |
| `sortBy` | string | ❌ | `entityName` | Sort field |

### 1.3 Get Entity by ID

Returns full detail for a single entity.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

### 1.4 Update Entity Status

Toggles operational status (`ACTIVE` / `INACTIVE`).

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{id}/status`
- **Auth:** `organizations:write`

#### Request Body

```json
{
  "status": "INACTIVE"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `status` | enum | ✅ | `ACTIVE`, `INACTIVE` | New operational status |

### 1.5 Approve Entity

Transitions entity from `PENDING` to `APPROVED`, seeding accounts and fiscal settings.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/approve`
- **Auth:** `organizations:write`

#### Request Body

```json
{}
```

Or with an optional fiscal-year override:

```json
{
  "fiscalYearOverride": {
    "fiscalStartMonth": 4,
    "fiscalStartDay": 1,
    "fiscalEndMonth": 3,
    "fiscalEndDay": 31
  }
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `fiscalYearOverride` | object | ❌ | — | Optional custom fiscal year; defaults to country template |
| `fiscalYearOverride.fiscalStartMonth` | int | ❌ | 1–12 | Start month of fiscal year |
| `fiscalYearOverride.fiscalStartDay` | int | ❌ | 1–31 | Start day of fiscal year |
| `fiscalYearOverride.fiscalEndMonth` | int | ❌ | 1–12 | End month of fiscal year |
| `fiscalYearOverride.fiscalEndDay` | int | ❌ | 1–31 | End day of fiscal year |

### 1.6 Reject Entity

Transitions entity from `PENDING` to `REJECTED`.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/reject`
- **Auth:** `organizations:write`

#### Request Body

```json
{
  "reason": "Entity name does not match registration documents"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `reason` | string | ✅ | max 500 chars | Reason for rejection |

### 1.7 List Accessible Entities

Returns entities the current user has access to.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/accessible`
- **Auth:** `isAuthenticated()`

### 1.8 List Access Grants

Lists user access grants for an entity.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:read`

### 1.9 Grant Access

Grants user access to an entity.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "authUserId": "550e8400-e29b-41d4-a716-446655440000",
  "entityRole": "VIEWER"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `authUserId` | UUID | ✅ | — | User to grant access to |
| `entityRole` | enum | ✅ | `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` | Access role |

### 1.10 Update Access Role

Updates role for an existing access grant.

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}/role`
- **Auth:** `users:write`

#### Request Body

```json
{
  "entityRole": "EDITOR"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `entityRole` | enum | ✅ | `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` | New access role |

### 1.11 Revoke Access

Revokes user access (soft-delete).

- **Method:** `DELETE`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}`
- **Auth:** `users:write`

### 1.12 Select Entity Context

Records entity context switch for the current user.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/context/select`
- **Auth:** `isAuthenticated()`

#### Request Body

```json
{
  "legalEntityId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `legalEntityId` | UUID | ✅ | — | Legal entity to set as active context |

---

## 2. Capital Injection / Funding

**Base path:** `/api/v1/finance/funding`

### 2.1 Create Capital Injection

Records a new capital injection with double-entry ledger postings. USD conversion is automatic for non-USD entities. Returns `rateWarning: true` when a lookback rate was used.

- **Method:** `POST`
- **Path:** `/api/v1/finance/funding/capital-injections`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "amount": 100000,
  "funding_date": "2026-05-18",
  "funding_source": "FOUNDER_EQUITY",
  "source_account_id": "3001bc86-f1c3-d57f-68a0-784274c157f3",
  "target_entity_code": "NEPUYT",
  "referenceNumber": "VCH-2026-001",
  "notes": "string",
  "destination_account_id": "7b1eca1d-05a5-c891-c9b5-bdb6105f0bbd",
  "source_entity_code": "US",
  "manual_exchange_rate": 0.012045,
  "manual_rate_justification": "string",
  "manual_rate_approved_by": "string"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `target_entity_code` | string | ✅ | 2–10 chars | Receiving entity code |
| `funding_source` | enum | ✅ | `FOUNDER_EQUITY`, `LOAN`, `GRANT`, `INTER_ENTITY_TRANSFER` | Funding category |
| `amount` | number | ✅ | 0.01–999T, 4 decimal places | Amount in target entity's local currency |
| `funding_date` | date | ✅ | Past or present | Date funds received |
| `source_account_id` | UUID | ✅ | — | Source funding account UUID |
| `destination_account_id` | UUID | ❌ | — | Auto-resolved if omitted |
| `source_entity_code` | string | ❌ | Required for inter-entity | Sending entity code |
| `referenceNumber` | string | ❌ | max 50 chars | External reference |
| `notes` | string | ❌ | max 500 chars | Free-text notes |
| `manual_exchange_rate` | number | ❌ | Positive, max 10+6 digits | Required when no automated rate exists |
| `manual_rate_justification` | string | ❌ | Required with manual rate | Justification note |
| `manual_rate_approved_by` | string | ❌ | Required with manual rate | Approver name |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Capital injection recorded successfully",
  "data": {
    "capitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
    "journalId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
    "transferId": null,
    "targetEntityCode": "INDIA",
    "sourceEntityCode": null,
    "amountLocal": 100000.0000,
    "currencyLocal": "INR",
    "amountUsd": 1200.0000,
    "exchangeRateUsed": 0.012000,
    "rateDateUsed": "2026-05-18",
    "rateSource": "API",
    "rateWarning": false,
    "message": "Capital injection created and posted to ledger"
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

### 2.2 List Capital Injections

Lists capital injections for a given entity (paginated). Response includes `rateWarning`.

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/capital-injections`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `entity_code` | string | ✅ | — | Target entity code |
| `page` | int | ❌ | `0` | Page index |
| `size` | int | ❌ | `20` | Page size |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "targetEntityCode": "INDIA",
        "targetEntityName": "India Operations",
        "sourceEntityCode": null,
        "fundingSource": "FOUNDER_EQUITY",
        "amountLocal": 100000.0000,
        "currencyLocal": "INR",
        "amountUsd": 1200.0000,
        "fundingDate": "2026-05-18",
        "exchangeRateUsed": 0.012000,
        "rateSource": "LOOKBACK",
        "injectionStatus": "POSTED",
        "referenceNumber": "VCH-2026-001",
        "createdBy": "admin@example.com",
        "rateWarning": true,
        "createdAt": "2026-05-18T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

### 2.3 Get Capital Injection Detail

Returns full detail including ledger entries. Ledger entries include `currencyLocal`, `exchangeRateUsed`, and `rateWarning`.

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/capital-injections/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Record retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "journalId": null,
    "transferId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
    "targetEntityCode": "INDIA",
    "targetEntityName": "India Operations",
    "sourceEntityCode": "US",
    "fundingSource": "INTER_ENTITY_TRANSFER",
    "fundingDate": "2026-05-18",
    "amountLocal": 50000.0000,
    "currencyLocal": "INR",
    "amountUsd": 600.0000,
    "exchangeRateUsed": 0.012000,
    "rateDateUsed": "2026-05-18",
    "rateSource": "API",
    "sourceAccountId": "550e8400-e29b-41d4-a716-446655440001",
    "sourceAccountName": "Cash - Operating",
    "destinationAccountId": "550e8400-e29b-41d4-a716-446655440002",
    "destinationAccountName": "Bank - Operating INR",
    "referenceNumber": "VCH-2026-002",
    "notes": "Inter-entity transfer for Q1 funding",
    "injectionStatus": "POSTED",
    "createdBy": "admin@example.com",
    "createdAt": "2026-05-18T10:00:00",
    "updatedAt": "2026-05-18T10:00:00",
    "ledgerEntries": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440010",
        "accountId": "550e8400-e29b-41d4-a716-446655440001",
        "accountName": "Cash - Operating",
        "accountCode": "1000",
        "entrySide": "CREDIT",
        "amountLocal": 600.0000,
        "currencyLocal": "USD",
        "amountUsd": 600.0000,
        "exchangeRateUsed": 1.000000,
        "rateWarning": false,
        "description": "Inter-entity transfer out"
      }
    ]
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

### 2.4 Update Capital Injection Status

Updates lifecycle status (e.g., `VOID`).

- **Method:** `PATCH`
- **Path:** `/api/v1/finance/funding/capital-injections/{id}/status`
- **Auth:** `organizations:write`

#### Request Body

```json
{
  "injection_status": "VOID",
  "reason": "Duplicate entry"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `injection_status` | enum | ✅ | `PENDING_REVIEW`, `POSTED`, `VOID`, `FAILED` | New lifecycle status |
| `reason` | string | ❌ | max 500 chars | Reason for status change |

### 2.5 Get Inter-Entity Transfer Detail

Returns reconciliation details for an inter-entity transfer.

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/inter-entity-transfers/{transferId}`
- **Auth:** `organizations:write`

---

## 3. Exchange Rates

**Base path:** `/api/v1/finance/exchange-rates`

> **Note:** The exchange rate module supports both automated API fetching (future) and manual rate management for air-gapped deployments. CSV upload enables bulk import of daily rates. All manual rates require a separate approval step via the [approve endpoint](#35-approve-exchange-rate) (LLR-FIN-04.1).

### 3.1 List Exchange Rates

Retrieve exchange rates, optionally filtered by pair/date.

- **Method:** `GET`
- **Path:** `/api/v1/finance/exchange-rates`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sourceCurrency` | string | ❌ | ISO-4217 source currency code (e.g., "INR", "NPR") |
| `targetCurrency` | string | ❌ | ISO-4217 target currency code (e.g., "USD") |
| `rateDate` | date | ❌ | Exact rate date (ISO format: `yyyy-MM-dd`) |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "sourceCurrency": "INR",
      "targetCurrency": "USD",
      "rateDate": "2026-05-18",
      "exchangeRate": 0.012000,
      "rateSource": "MANUAL",
      "status": "ACTIVE",
      "createdAt": "2026-05-18T10:00:00"
    }
  ],
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

### 3.2 Get Exchange Rate by ID

Retrieve one exchange-rate record with full detail including audit fields.

- **Method:** `GET`
- **Path:** `/api/v1/finance/exchange-rates/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Exchange-rate UUID |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Record retrieved successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "sourceCurrency": "INR",
    "targetCurrency": "USD",
    "rateDate": "2026-05-18",
    "exchangeRate": 0.012000,
    "rateSource": "MANUAL",
    "status": "ACTIVE",
    "createdBy": null,
    "approvedBy": null,
    "createdAt": "2026-05-18T10:00:00",
    "updatedAt": null
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

> **⚠️ Known Limitation:** `createdBy`, `approvedBy`, and `updatedAt` return `null` on the GET-by-ID endpoint. The controller currently adapts from [`ExchangeRateSummaryResponse`](src/main/java/com/af/novadesk/api/finance/dto/ExchangeRateSummaryResponse.java) which excludes these audit fields. The POST/PUT response includes them correctly.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Exchange rate not found |

### 3.3 Create Exchange Rate

Creates a new manual exchange rate. The `rateSource` is always `MANUAL` for admin-entered rates. `createdBy` is resolved server-side from the JWT. Approval is done separately via [3.5 Approve](#35-approve-exchange-rate).

- **Method:** `POST`
- **Path:** `/api/v1/finance/exchange-rates`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "source_currency": "INR",
  "target_currency": "USD",
  "rate_date": "2026-05-22",
  "exchange_rate": 0.012045,
  "notes": "Central bank published rate for May 22"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `source_currency` | string | ✅ | Exactly 3 chars, ISO 4217 | Source currency code (e.g., "INR", "NPR") |
| `target_currency` | string | ✅ | Exactly 3 chars, ISO 4217 | Target currency code (e.g., "USD") |
| `rate_date` | date | ✅ | Past or present (`yyyy-MM-dd`) | Effective date |
| `exchange_rate` | number | ✅ | Positive, max 10+6 digits | Conversion rate |
| `notes` | string | ❌ | max 500 chars | Optional justification |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Exchange rate created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "sourceCurrency": "INR",
    "targetCurrency": "USD",
    "rateDate": "2026-05-22",
    "exchangeRate": 0.012045,
    "rateSource": "MANUAL",
    "status": "ACTIVE",
    "createdBy": "finance.admin@example.com",
    "approvedBy": null,
    "createdAt": "2026-05-22T10:00:00",
    "updatedAt": "2026-05-22T10:00:00"
  },
  "timestamp": "2026-05-22T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 409 | Rate already exists for this currency pair and date |

### 3.4 Update Exchange Rate

Updates rate value and date. Currency pair is immutable.

- **Method:** `PUT`
- **Path:** `/api/v1/finance/exchange-rates/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

Same as [3.3 Create Exchange Rate](#33-create-exchange-rate). All fields are required.

```json
{
  "source_currency": "INR",
  "target_currency": "USD",
  "rate_date": "2026-05-22",
  "exchange_rate": 0.012045,
  "notes": "Adjusted rate per central bank update"
}
```

> **Note:** `source_currency` and `target_currency` are immutable — changing them will result in a `400` error.

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure or attempt to change currency pair |
| 404 | Exchange rate not found |

### 3.5 Approve Exchange Rate

Records an approver for a manually-entered rate. Approver identity is resolved server-side from the JWT.

- **Method:** `PATCH`
- **Path:** `/api/v1/finance/exchange-rates/{id}/approve`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

> **No request body required.** The approver identity is resolved server-side from the JWT.

### 3.6 Delete Exchange Rate

Soft-deletes by setting status to `INACTIVE`.

- **Method:** `DELETE`
- **Path:** `/api/v1/finance/exchange-rates/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

### 3.7 CSV Upload Exchange Rates

Imports exchange rates from CSV for air-gapped deployments (LLR-FIN-04.2). Duplicate rows are skipped. Validation errors reported per-row.

- **Method:** `POST`
- **Path:** `/api/v1/finance/exchange-rates/csv-upload`
- **Content-Type:** `multipart/form-data`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### CSV Format

```csv
date, currency_pair, rate
2026-04-23, INR-USD, 0.012045
2026-04-23, NPR-USD, 0.007512
2026-04-24, INR-USD, 0.012100
```

| Column | Format | Example | Notes |
|--------|--------|---------|-------|
| `date` | `YYYY-MM-DD` | `2026-04-23` | Not future |
| `currency_pair` | `XXX-YYY` | `INR-USD` | Both 3-char ISO 4217 |
| `rate` | decimal | `0.012045` | Positive, ≤ configured ceiling |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "CSV import completed",
  "data": {
    "totalRows": 150,
    "successCount": 140,
    "skippedCount": 5,
    "errorCount": 5,
    "errors": [
      { "lineNumber": 23, "message": "Rate date cannot be in the future" },
      { "lineNumber": 87, "message": "Exchange rate must be positive" }
    ]
  },
  "timestamp": "2026-05-22T11:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Invalid file |
| 422 | CSV parsing failed |

### 3.8 Daily Exchange Rate Sync Scheduler

Automated scheduler for fetching exchange rates from an external API (future integration). Currently stubbed — CSV upload is the production path for air-gapped deployments.

#### Outbox Events

| Event Type | Payload | Purpose |
|-----------|---------|---------|
| `EXCHANGE_RATE_SYNC_COMPLETED` | `{ rateId, sourceCurrency, targetCurrency, rateDate, exchangeRate, rateSource }` | Successful API sync |
| `EXCHANGE_RATE_SYNC_FAILED` | `{ sourceCurrency, targetCurrency, rateDate, error, retryCount }` | All retries exhausted (DLQ) |
| `EXCHANGE_RATE_MANUALLY_UPDATED` | `{ rateId, sourceCurrency, targetCurrency, rateDate, exchangeRate, rateSource, updatedBy }` | Admin CRUD / approve |
| `EXCHANGE_RATE_CSV_IMPORTED` | `{ totalRows, successCount, skippedCount, errorCount, uploadedBy }` | CSV import success |
| `EXCHANGE_RATE_CSV_IMPORT_FAILED` | `{ fileName, error, uploadedBy }` | CSV file parse failure |

---

## 4. Funding Accounts

**Base path:** `/api/v1/finance/accounts`

Funding accounts (`fa_accounts` table) are automatically seeded when a legal entity is approved. Each entity gets accounts for `BANK_OPERATING`, `CASH`, `FOUNDER_EQUITY`, `LOAN_PAYABLE`, `GRANT_INCOME`, and inter-entity accounts based on its country template.

### 4.1 List Accounts

Retrieve all funding accounts, optionally filtered by legal entity.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `legalEntityId` | UUID | ❌ | Filter accounts belonging to a specific legal entity |

#### Example: Filter by entity

```
GET /api/v1/finance/accounts?legalEntityId=550e8400-e29b-41d4-a716-446655440000
```

### 4.2 Get Account by ID

Retrieve one funding account by UUID.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

---

## 5. Financial Reports

**Base path:** `/api/v1/finance/reports`

> **New in LLR-FIN-04.5.** Supports single-entity ledger reports with currency selector (USD vs local) and multi-entity consolidated reports (always in USD).

### 5.1 Ledger Report

Generates a paginated ledger report for a single legal entity with a currency selector.

- **Method:** `GET`
- **Path:** `/api/v1/finance/reports/ledger`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `entityId` | UUID | ✅ | — | Legal entity UUID |
| `startDate` | date | ❌ | — | Start date (inclusive) |
| `endDate` | date | ❌ | — | End date (inclusive) |
| `currency` | string | ❌ | `USD` | `USD` or `LOCAL` |
| `accountId` | UUID | ❌ | — | Filter by account |
| `page` | int | ❌ | `0` | Page index |
| `size` | int | ❌ | `20` | Page size |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": {
    "rows": [
      {
        "entryId": "550e8400-e29b-41d4-a716-446655440010",
        "entryDate": "2026-05-18",
        "accountName": "Bank - Operating INR",
        "accountCode": "1001",
        "entrySide": "DEBIT",
        "amount": 100000.0000,
        "currency": "INR",
        "description": "Capital injection — FOUNDER_EQUITY",
        "referenceType": "CAPITAL_INJECTION",
        "referenceId": "550e8400-e29b-41d4-a716-446655440000",
        "exchangeRateUsed": 0.012000,
        "rateWarning": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "reportingCurrency": "INR",
    "entityCode": "INDIA",
    "entityName": "India Operations",
    "entityBaseCurrency": "INR"
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

### 5.2 Consolidated Report

Multi-entity consolidated report always in USD for cross-entity comparability.

- **Method:** `GET`
- **Path:** `/api/v1/finance/reports/consolidated`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `entityIds` | UUID[] | ✅ | List of entity UUIDs |
| `startDate` | date | ❌ | Start date (inclusive) |
| `endDate` | date | ❌ | End date (inclusive) |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": {
    "entitySummaries": [
      {
        "entityCode": "INDIA",
        "entityName": "India Operations",
        "baseCurrency": "INR",
        "totalDebitsLocal": 1500000.0000,
        "totalCreditsLocal": 200000.0000,
        "totalDebitsUsd": 18000.0000,
        "totalCreditsUsd": 2400.0000
      },
      {
        "entityCode": "NEPAL",
        "entityName": "Nepal Operations",
        "baseCurrency": "NPR",
        "totalDebitsLocal": 800000.0000,
        "totalCreditsLocal": 100000.0000,
        "totalDebitsUsd": 6000.0000,
        "totalCreditsUsd": 750.0000
      }
    ],
    "totalDebitsUsd": 24000.0000,
    "totalCreditsUsd": 3150.0000,
    "netPositionUsd": 20850.0000,
    "startDate": "2026-01-01",
    "endDate": "2026-05-18"
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

---

## 6. Vendors

**Base path:** `/api/v1/expense/vendors`

> Vendor (payee) registry management per LLR-FIN-03.3. Vendors are scoped to the caller's organization. Inactive vendors are excluded from the expense form autocomplete.

### 6.1 Create Vendor

Registers a new vendor in the organization's registry.

- **Method:** `POST`
- **Path:** `/api/v1/expense/vendors`
- **Auth:** `VENDOR_CREATE`
- **Status:** `201 Created`

#### Request Body

```json
{
  "vendor_name": "Amazon Web Services",
  "vendor_type": "SERVICE_PROVIDER",
  "tax_id": "92-0070768",
  "default_account_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `vendor_name` | string | ✅ | max 100 chars | Full legal or trading name |
| `vendor_type` | enum | ✅ | `SERVICE_PROVIDER`, `LANDLORD`, `UTILITY`, `SUPPLIER`, `CONTRACTOR`, `GOVERNMENT`, `OTHER` | Business category |
| `tax_id` | string | ❌ | max 50 chars | Tax or company registration number |
| `default_account_id` | UUID | ❌ | — | Default expense account for form auto-fill |

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation error |
| 409 | Vendor name already exists in this organization |

### 6.2 List Vendors

Returns a paginated list of vendors in the organization with optional name search.

- **Method:** `GET`
- **Path:** `/api/v1/expense/vendors`
- **Auth:** `VENDOR_READ`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | int | ❌ | `0` | Page number (0-based) |
| `size` | int | ❌ | `20` | Page size |
| `sortBy` | string | ❌ | `vendorName` | Sort field |
| `search` | string | ❌ | — | Name fragment for autocomplete search |

### 6.3 Get Vendor by ID

Retrieves a single vendor scoped to the caller's organization.

- **Method:** `GET`
- **Path:** `/api/v1/expense/vendors/{id}`
- **Auth:** `VENDOR_READ`
- **Status:** `200 OK`

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Vendor not found |

### 6.4 Update Vendor

Updates vendor name, type, tax ID, or default account.

- **Method:** `PATCH`
- **Path:** `/api/v1/expense/vendors/{id}`
- **Auth:** `VENDOR_UPDATE`

#### Request Body

Same fields as [6.1 Create Vendor](#61-create-vendor).

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation error |
| 404 | Vendor not found |
| 409 | Vendor name already taken |

### 6.5 Update Vendor Status

Activates (`ACTIVE`) or deactivates (`INACTIVE`) a vendor.

- **Method:** `PATCH`
- **Path:** `/api/v1/expense/vendors/{id}/status`
- **Auth:** `VENDOR_UPDATE`

#### Request Body

```json
{
  "status": "INACTIVE"
}
```

> Inactive vendors are excluded from the expense form autocomplete.

---

## 7. Expense Transactions

**Base path:** `/api/v1/expense/transactions`

> Manual expense recording per LLR-FIN-03. Every POST creates two balanced ledger entries (CREDIT on source + DEBIT on destination) in the same transaction. Attachments are stored in object storage (MinIO) and encrypted at rest.

### 7.1 Record Expense

Records a new manual expense against the caller's active legal entity and posts double-entry ledger entries.

- **Method:** `POST`
- **Path:** `/api/v1/expense/transactions`
- **Auth:** `EXPENSE_CREATE`
- **Status:** `201 Created`

#### Request Body

```json
{
  "legal_entity_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "vendor_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "expense_date": "2026-05-22",
  "amount": 500.00,
  "payment_method": "BANK_TRANSFER",
  "source_account_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "destination_account_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "invoice_receipt_number": "INV-2026-00123",
  "description": "Monthly server bill - May 2026"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `legal_entity_id` | UUID | ✅ | — | Legal entity this expense is recorded against |
| `vendor_id` | UUID | ✅ | — | Vendor (payee) UUID |
| `expense_date` | date | ✅ | Past or present | Date the expense was incurred |
| `amount` | number | ✅ | 0.01–999T, 4 decimal places | Transaction amount in entity's base currency |
| `payment_method` | enum | ✅ | `BANK_TRANSFER`, `CREDIT_CARD`, `CASH`, `CHECK` | Settlement method |
| `source_account_id` | UUID | ✅ | — | Account being CREDITED (funds leave this account) |
| `destination_account_id` | UUID | ✅ | — | Account being DEBITED (expense is recognised here) |
| `invoice_receipt_number` | string | ❌ | max 50 chars | Vendor-issued invoice number for reconciliation |
| `description` | string | ✅ | max 500 chars | What the expense was for |

> **Validation:** The service layer enforces `sourceAccountId ≠ destinationAccountId` before posting ledger entries.

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation error (including source == destination account) |
| 404 | Vendor or account not found |
| 403 | Insufficient permissions |

### 7.2 List Expenses

Returns a paginated list of expense transactions for the caller's active legal entity.

- **Method:** `GET`
- **Path:** `/api/v1/expense/transactions`
- **Auth:** `EXPENSE_READ`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | int | ❌ | `0` | Page number (0-based) |
| `size` | int | ❌ | `20` | Page size |
| `sortBy` | string | ❌ | `expenseDate` | Sort field |
| `status` | string | ❌ | — | Filter by transaction status: `POSTED` or `VOID` |

### 7.3 Get Expense by ID

Retrieves a single expense transaction scoped to the caller's active legal entity.

- **Method:** `GET`
- **Path:** `/api/v1/expense/transactions/{id}`
- **Auth:** `EXPENSE_READ`
- **Status:** `200 OK`

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Expense not found |

### 7.4 Void Expense

Cancels a POSTED expense by creating offsetting reversal ledger entries and marking the transaction as VOID. A voided transaction is immutable — it cannot be reinstated.

- **Method:** `POST`
- **Path:** `/api/v1/expense/transactions/{id}/void`
- **Auth:** `EXPENSE_VOID`

#### Request Body

```json
{
  "void_reason": "Entered in wrong period"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `void_reason` | string | ✅ | — | Reason for voiding |

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Transaction is already VOID |
| 404 | Expense not found |
| 403 | Insufficient permissions |

### 7.5 Upload Attachment

Encrypts and stores a file (PDF, PNG, JPG, JPEG; max 5 MB) in object storage and links it to the expense transaction.

- **Method:** `POST`
- **Path:** `/api/v1/expense/transactions/{id}/attachments`
- **Content-Type:** `multipart/form-data`
- **Auth:** `EXPENSE_CREATE`
- **Status:** `201 Created`

#### Form Fields

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `file` | file | ✅ | PDF, PNG, JPG, JPEG — max 5 MB | Invoice or receipt |

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Unsupported file type or file exceeds 5 MB |
| 404 | Expense transaction not found |
| 403 | Insufficient permissions |

### 7.6 List Attachments

Returns metadata for all files attached to the given expense transaction. Requires `VIEW_FINANCIAL_DOCUMENTS` permission. File content is accessed via the pre-signed URL in each record.

- **Method:** `GET`
- **Path:** `/api/v1/expense/transactions/{id}/attachments`
- **Auth:** `VIEW_FINANCIAL_DOCUMENTS`
- **Status:** `200 OK`

### 7.7 Delete Attachment

Removes the attachment record from the database and deletes the object from storage. Only allowed on `POSTED` (non-voided) transactions.

- **Method:** `DELETE`
- **Path:** `/api/v1/expense/transactions/{transactionId}/attachments/{attachmentId}`
- **Auth:** `EXPENSE_CREATE`
- **Status:** `204 No Content`

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Cannot delete attachment on a VOID transaction |
| 404 | Transaction or attachment not found |
| 403 | Insufficient permissions |

---

## 8. Common Error Response Shapes

### Validation Error (400)

```json
{
  "success": false,
  "code": 400,
  "message": "Validation failed. Please check the errors and try again",
  "data": null,
  "metadata": {
    "errors": [
      { "field": "amount", "message": "Amount must be at least 0.01", "code": "DecimalMin" }
    ]
  },
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

### Not Found (404)

```json
{
  "success": false,
  "code": 404,
  "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

### Unauthorized (401)

```json
{
  "success": false,
  "code": 401,
  "message": "Not authenticated",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

### Conflict (409)

```json
{
  "success": false,
  "code": 409,
  "message": "Entity with name 'Test Entity' already exists",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

### Forbidden (403)

```json
{
  "success": false,
  "code": 403,
  "message": "Access denied",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

### Server Error (500)

```json
{
  "success": false,
  "code": 500,
  "message": "An unexpected error occurred",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```
