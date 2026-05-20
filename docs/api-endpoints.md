# af-novadesk-api — REST API Endpoints Reference

> **Base URL:** `http://localhost:8080` (local) / `https://{env}.novadesk.auroraforge.com` (deployed)
>
> **Auth:** All endpoints require a JWT Bearer token in the `Authorization` header.
>
> **Response Envelope:** Every response is wrapped in a standard `ApiResponse<T>` envelope:

```json
{
  "success": true,
  "code": 200,
  "message": "Human-readable message",
  "data": { /* payload */ },
  "metadata": { /* optional: pagination, errors, etc. */ },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

---

## Table of Contents

1. [Legal Entities (Multi-Entity Management)](#1-legal-entities)
   - [1.1 Create Entity](#11-create-entity)
   - [1.2 List Entities](#12-list-entities)
   - [1.3 Get Entity by ID](#13-get-entity-by-id)
   - [1.4 Update Status](#14-update-status)
   - [1.5 Approve Entity](#15-approve-entity)
   - [1.6 Reject Entity](#16-reject-entity)
   - [1.7 List Accessible Entities](#17-list-accessible-entities)
   - [1.8 List Access Grants](#18-list-access-grants)
   - [1.9 Grant Access](#19-grant-access)
   - [1.10 Update Role](#110-update-role)
   - [1.11 Revoke Access](#111-revoke-access)
   - [1.12 Select Entity Context](#112-select-entity-context)
2. [Capital Injection (Funding)](#2-capital-injection)
   - [2.1 Record Capital Injection](#21-record-capital-injection)
3. [Exchange Rates](#3-exchange-rates)
   - [3.1 List Exchange Rates](#31-list-exchange-rates)
   - [3.2 Get Exchange Rate by ID](#32-get-exchange-rate-by-id)
4. [Funding Accounts](#4-funding-accounts)
   - [4.1 List Accounts](#41-list-accounts)
   - [4.2 Get Account by ID](#42-get-account-by-id)

---

## 1. Legal Entities

**Base path:** `/api/v1/legal-entities`

### 1.1 Create Entity

Creates a new legal entity in `PENDING` approval state.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "entityName": "Test Entity",
  "entityCode": "TEST01",
  "country": "US",
  "taxId": "12-3456789",
  "incorporationDate": "2020-01-15"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `entityName` | string | ✅ | Max 100 chars |
| `entityCode` | string | ✅ | 2–10 chars, uppercase alphanumeric `^[A-Z0-9]+$` |
| `country` | enum | ✅ | `US`, `IN`, `NP` |
| `taxId` | string | ❌ | Max 50 chars |
| `incorporationDate` | date | ✅ | Must be past or present |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Legal entity created and pending approval",
  "data": {
    "id": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityCode": "TEST01",
    "country": "US",
    "baseCurrency": "USD",
    "taxId": "12-3456789",
    "incorporationDate": "2020-01-15",
    "approvalStatus": "PENDING",
    "status": "ACTIVE",
    "fiscalYearSetting": null,
    "chartOfAccounts": null,
    "bankAccounts": null,
    "createdAt": "2026-05-20T10:00:00",
    "updatedAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure (missing/invalid fields) |
| 409 | Duplicate entity name or code |

---

### 1.2 List Entities

Lists all entities within the caller's organization (paginated).

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities`
- **Auth:** `organizations:read`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Zero-based page index |
| `size` | int | `20` | Page size |
| `sortBy` | string | `entityName` | Sort field |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "00000000-0000-0000-0000-000000000010",
        "entityName": "Test Entity",
        "entityCode": "TEST01",
        "country": "US",
        "baseCurrency": "USD",
        "status": "ACTIVE",
        "approvalStatus": "PENDING"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

---

### 1.3 Get Entity by ID

Returns full detail for a single entity.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}`
- **Auth:** `organizations:read`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity ID |

#### Response Body (200)

Same shape as the create response `data` object (includes `fiscalYearSetting`, `chartOfAccounts`, `bankAccounts` when populated).

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Entity not found |

---

### 1.4 Update Status

Activates or deactivates an entity.

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{id}/status`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "status": "INACTIVE"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `status` | enum | ✅ | `ACTIVE` or `INACTIVE` |

#### Response Body (200)

Returns the updated `LegalEntityDto`.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Entity not found |

---

### 1.5 Approve Entity

Finance-team approves a pending entity, triggering CoA + bank account seeding.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/approve`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "fiscalYearOverride": null
}
```

Or with a fiscal year override:

```json
{
  "fiscalYearOverride": {
    "fiscalStartMonth": 4,
    "fiscalStartDay": 1,
    "fiscalEndMonth": 3,
    "fiscalEndDay": 31,
    "currentFiscalYear": 2025,
    "periodsPerYear": 12
  }
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `fiscalYearOverride` | object | ❌ | If provided, all sub-fields are required |

**FiscalYearOverride sub-fields:**

| Field | Type | Constraints |
|-------|------|-------------|
| `fiscalStartMonth` | int | 1–12 |
| `fiscalStartDay` | int | 1–31 |
| `fiscalEndMonth` | int | 1–12 |
| `fiscalEndDay` | int | 1–31 |
| `currentFiscalYear` | int | — |
| `periodsPerYear` | int | 1–52 |

#### Response Body (200)

Returns the approved `LegalEntityDto` with `approvalStatus: "APPROVED"`, plus seeded `chartOfAccounts` and `bankAccounts`.

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Entity is not in `PENDING` state |
| 404 | Entity not found |

---

### 1.6 Reject Entity

Finance-team rejects a pending entity.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/reject`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "reason": "Incomplete documentation"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `reason` | string | ✅ | Max 500 chars |

#### Response Body (200)

Returns the rejected `LegalEntityDto` with `approvalStatus: "REJECTED"`.

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Entity is not in `PENDING` state |
| 404 | Entity not found |

---

### 1.7 List Accessible Entities

Returns entities the current user has active access to. Drives the entity selector dropdown.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/accessible`
- **Auth:** `isAuthenticated()` (any authenticated user)
- **Status:** `200 OK`

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Accessible entities retrieved",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000010",
      "entityName": "Test Entity",
      "entityCode": "TEST01",
      "country": "US",
      "baseCurrency": "USD",
      "status": "ACTIVE",
      "approvalStatus": "APPROVED"
    }
  ],
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

---

### 1.8 List Access Grants

Lists all user access grants for an entity (admin view).

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:read`
- **Status:** `200 OK`

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000020",
      "authUserId": "00000000-0000-0000-0000-000000000002",
      "email": "john.doe@example.com",
      "displayName": "John Doe",
      "legalEntityId": "00000000-0000-0000-0000-000000000010",
      "entityName": "Test Entity",
      "entityRole": "VIEWER",
      "status": "ACTIVE",
      "lastAccessedAt": null,
      "createdAt": "2026-05-20T10:00:00"
    }
  ],
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

---

### 1.9 Grant Access

Grants a user access to an entity with a given role.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "authUserId": "00000000-0000-0000-0000-000000000002",
  "entityRole": "VIEWER"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `authUserId` | UUID | ✅ | — |
| `entityRole` | string | ✅ | One of: `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` |

#### Response Body (201)

Returns the created `EntityUserAccessDto`.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Entity or shadow user not found |
| 409 | Access already exists for this user+entity |

---

### 1.10 Update Role

Updates the role of an existing access grant.

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}/role`
- **Auth:** `users:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "entityRole": "ADMIN"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `entityRole` | string | ✅ | One of: `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` |

#### Response Body (200)

Returns the updated `EntityUserAccessDto`.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Access grant not found |

---

### 1.11 Revoke Access

Revokes a user's access to an entity (soft-delete by setting status to `INACTIVE`).

- **Method:** `DELETE`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}`
- **Auth:** `users:write`
- **Status:** `200 OK`

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Access revoked",
  "data": null,
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Access grant not found |

---

### 1.12 Select Entity Context

Records an entity context switch and returns the active context. Validates the user has **active** access to the entity.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/context/select`
- **Auth:** `isAuthenticated()` (any authenticated user)
- **Status:** `200 OK`

#### Request Body

```json
{
  "legalEntityId": "00000000-0000-0000-0000-000000000010"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `legalEntityId` | UUID | ✅ | — |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Entity context selected",
  "data": {
    "legalEntityId": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityCode": "TEST01",
    "baseCurrency": "USD",
    "selectedAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 403 | User does not have active access to this entity |
| 404 | Entity not found |

---

## 2. Capital Injection

**Base path:** `/api/v1/finance/funding`

### 2.1 Record Capital Injection

Creates a balanced double-entry posting for a funding event. For inter-entity transfers, postings are written across both entity ledgers.

- **Method:** `POST`
- **Path:** `/api/v1/finance/funding/capital-injections`
- **Auth:** `FINANCE_CAPITAL_INJECTION_WRITE`
- **Status:** `201 Created`

#### Request Body

```json
{
  "target_entity_code": "INDIA",
  "funding_source": "FOUNDER_EQUITY",
  "amount": 100000.00,
  "funding_date": "2026-05-18",
  "source_account_id": "550e8400-e29b-41d4-a716-446655440000",
  "destination_account_id": null,
  "source_entity_code": null,
  "reference_number": "VCH-2026-001",
  "notes": "Initial capital injection",
  "manual_exchange_rate": null,
  "manual_rate_justification": null,
  "manual_rate_approved_by": null
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `target_entity_code` | string | ✅ | 2–10 chars |
| `funding_source` | enum | ✅ | e.g. `FOUNDER_EQUITY`, `INTER_ENTITY_TRANSFER`, etc. |
| `amount` | decimal | ✅ | ≥ 0.01, max 15 integer + 4 decimal digits |
| `funding_date` | date | ✅ | Must be past or present |
| `source_account_id` | UUID | ✅ | — |
| `destination_account_id` | UUID | ❌ | Auto-resolved to default `BANK_OPERATING`/`CASH` account when omitted |
| `source_entity_code` | string | ❌ | Required only for `INTER_ENTITY_TRANSFER`; 2–10 chars |
| `reference_number` | string | ❌ | Max 50 chars |
| `notes` | string | ❌ | Max 500 chars |
| `manual_exchange_rate` | decimal | ❌ | Required when no rate exists in the table; positive, max 10 int + 6 decimal digits |
| `manual_rate_justification` | string | ❌ | Required when manual rate supplied; max 500 chars |
| `manual_rate_approved_by` | string | ❌ | Required when manual rate supplied; max 100 chars |

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
    "message": "Capital injection created and posted to ledger"
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 404 | Entity or account not found |
| 422 | Exchange rate unavailable and no manual rate provided |
| 500 | Unexpected server error |

---

## 3. Exchange Rates

**Base path:** `/api/v1/finance/exchange-rates`

### 3.1 List Exchange Rates

Retrieve exchange rates, optionally filtered by pair/date.

- **Method:** `GET`
- **Path:** `/api/v1/finance/exchange-rates`
- **Auth:** `FINANCE_READ`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `sourceCurrency` | string | ❌ | ISO-4217 source currency code |
| `targetCurrency` | string | ❌ | ISO-4217 target currency code |
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
      "rateSource": "API",
      "status": "ACTIVE",
      "createdAt": "2026-05-18T10:00:00"
    }
  ],
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

---

### 3.2 Get Exchange Rate by ID

Retrieve one exchange-rate record by UUID.

- **Method:** `GET`
- **Path:** `/api/v1/finance/exchange-rates/{id}`
- **Auth:** `FINANCE_READ`
- **Status:** `200 OK`

#### Response Body (200)

Returns a single `ExchangeRateSummaryResponse` object.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Exchange rate not found |

---

## 4. Funding Accounts

**Base path:** `/api/v1/finance/accounts`

### 4.1 List Accounts

Retrieve all funding accounts.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts`
- **Auth:** `FINANCE_READ`
- **Status:** `200 OK`

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "legalEntityId": "00000000-0000-0000-0000-000000000010",
      "accountCode": "3100",
      "accountName": "Founder Equity",
      "accountRole": "EQUITY",
      "accountType": "LIABILITY",
      "currencyCode": "USD",
      "status": "ACTIVE",
      "createdAt": "2026-05-18T10:00:00"
    }
  ],
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

---

### 4.2 Get Account by ID

Retrieve one funding account by UUID.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts/{id}`
- **Auth:** `FINANCE_READ`
- **Status:** `200 OK`

#### Response Body (200)

Returns a single `AccountSummaryResponse` object.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Account not found |

---

## Common Error Response Shapes

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

### Unprocessable Entity (422)

```json
{
  "success": false,
  "code": 422,
  "message": "Exchange rate unavailable for INR/USD on 2026-05-18. Provide a manual rate.",
  "data": null,
  "timestamp": "2026-05-18T14:31:00.123Z"
}
```

---

## Summary of All Endpoints

| # | Method | Path | Auth | Status |
|---|--------|------|------|--------|
| 1.1 | POST | `/api/v1/legal-entities` | `organizations:write` | 201 |
| 1.2 | GET | `/api/v1/legal-entities` | `organizations:read` | 200 |
| 1.3 | GET | `/api/v1/legal-entities/{id}` | `organizations:read` | 200 |
| 1.4 | PATCH | `/api/v1/legal-entities/{id}/status` | `organizations:write` | 200 |
| 1.5 | POST | `/api/v1/legal-entities/{id}/approve` | `organizations:write` | 200 |
| 1.6 | POST | `/api/v1/legal-entities/{id}/reject` | `organizations:write` | 200 |
| 1.7 | GET | `/api/v1/legal-entities/accessible` | `isAuthenticated()` | 200 |
| 1.8 | GET | `/api/v1/legal-entities/{id}/access` | `users:read` | 200 |
| 1.9 | POST | `/api/v1/legal-entities/{id}/access` | `users:write` | 201 |
| 1.10 | PATCH | `/api/v1/legal-entities/{entityId}/access/{accessId}/role` | `users:write` | 200 |
| 1.11 | DELETE | `/api/v1/legal-entities/{entityId}/access/{accessId}` | `users:write` | 200 |
| 1.12 | POST | `/api/v1/legal-entities/context/select` | `isAuthenticated()` | 200 |
| 2.1 | POST | `/api/v1/finance/funding/capital-injections` | `FINANCE_CAPITAL_INJECTION_WRITE` | 201 |
| 3.1 | GET | `/api/v1/finance/exchange-rates` | `FINANCE_READ` | 200 |
| 3.2 | GET | `/api/v1/finance/exchange-rates/{id}` | `FINANCE_READ` | 200 |
| 4.1 | GET | `/api/v1/finance/accounts` | `FINANCE_READ` | 200 |
| 4.2 | GET | `/api/v1/finance/accounts/{id}` | `FINANCE_READ` | 200 |
