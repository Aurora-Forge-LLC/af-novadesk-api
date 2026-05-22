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
   - 3.3 [Create Exchange Rate (Admin)](#33-create-exchange-rate-admin)
   - 3.4 [Daily Exchange Rate Sync Scheduler](#34-daily-exchange-rate-sync-scheduler)
4. [Funding Accounts](#4-funding-accounts)
   - 4.1 [List Accounts](#41-list-accounts)
   - 4.2 [Get Account by ID](#42-get-account-by-id)
5. [Common Error Response Shapes](#5-common-error-response-shapes)

---

## 1. Legal Entities

**Base path:** `/api/v1/legal-entities`

---

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
  "entityCode": "TEST01",
  "country": "US",
  "taxId": "12-3456789",
  "incorporationDate": "2020-01-15"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `entityName` | string | ✅ | max 100 chars | The legal name of the entity (e.g., "India Operations Pvt Ltd") | User-defined during entity registration |
| `entityCode` | string | ✅ | 2–10 chars, uppercase alphanumeric (`^[A-Z0-9]+$`) | A short, unique identifier for the entity (e.g., "INDIA", "NEPUYT", "US"). Used as `target_entity_code` / `source_entity_code` in capital injections. | User-defined; must be unique within the organization |
| `country` | enum | ✅ | — | The country of incorporation. Determines the entity's base currency and the Chart of Accounts template used during approval. | See **CountryCode** enum below |
| `taxId` | string | ❌ | max 50 chars | Tax registration number (e.g., EIN for US, PAN for India) | User-defined |
| `incorporationDate` | date | ✅ | Must be past or present (`yyyy-MM-dd`) | The date the entity was legally incorporated | User-defined |

##### CountryCode Enum Values

| Value | Default Currency | Description |
|-------|-----------------|-------------|
| `US` | `USD` | United States — Chart of Accounts template includes US-specific accounts |
| `IN` | `INR` | India — Chart of Accounts template includes India-specific accounts |
| `NP` | `NPR` | Nepal — Chart of Accounts template includes Nepal-specific accounts |

> **Note:** The `country` value determines the base currency and the set of accounts seeded on approval. For example, an entity with `country: "NP"` gets accounts with currency `NPR`.

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
| 400 | Validation failure (e.g., missing required field) |
| 401 | Not authenticated |
| 409 | Duplicate entity name or code |
| 500 | Unexpected server error |

---

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

Returns full detail for a single entity, including fiscal year settings, chart of accounts, and bank accounts when populated.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID (retrieved from [List Entities](#12-list-entities) or [Create Entity](#11-create-entity) response) |

#### Response Body (200)

Returns a full `LegalEntityDto` object (same shape as the create response, but with `fiscalYearSetting`, `chartOfAccounts`, and `bankAccounts` populated if the entity has been approved).

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Entity not found |
| 500 | Unexpected server error |

---

### 1.4 Update Entity Status

Toggles the operational status of a legal entity between `ACTIVE` and `INACTIVE`.

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{id}/status`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID |

#### Request Body

```json
{
  "status": "INACTIVE"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `status` | enum | ✅ | — | The new operational status for the entity | See **Status** enum below |

##### Status Enum Values

| Value | Description |
|-------|-------------|
| `ACTIVE` | Entity is operational and can be used in transactions |
| `INACTIVE` | Entity is deactivated; cannot be used in new transactions |
| `SUSPENDED` | Entity is temporarily suspended |
| `DELETED` | Entity is soft-deleted |

> **Note:** This endpoint only toggles between `ACTIVE` and `INACTIVE`. The `SUSPENDED` and `DELETED` statuses are reserved for future use or internal processes.

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Entity status updated",
  "data": {
    "id": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityCode": "TEST01",
    "country": "US",
    "baseCurrency": "USD",
    "approvalStatus": "APPROVED",
    "status": "INACTIVE",
    "createdAt": "2026-05-20T10:00:00",
    "updatedAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 404 | Entity not found |
| 500 | Unexpected server error |

---

### 1.5 Approve Entity

Transitions a `PENDING` entity to `APPROVED` status. Within the same transaction:
- Seeds the **Chart of Accounts** from the country template
- Seeds **default bank accounts**
- Seeds **funding accounts** (`fa_accounts` table) — these are the accounts used in capital injection transactions
- Initialises `FiscalYearSetting` (with optional override)
- Publishes a `LEGAL_ENTITY_APPROVED` outbox event

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/approve`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID (retrieved from [List Entities](#12-list-entities) or [Create Entity](#11-create-entity) response) |

#### Request Body

```json
{
  "fiscalYearOverride": {
    "fiscalStartMonth": 4,
    "fiscalStartDay": 1,
    "fiscalEndMonth": 3,
    "fiscalEndDay": 31,
    "currentFiscalYear": 2026,
    "periodsPerYear": 12
  }
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `fiscalYearOverride` | object | ❌ | — | Optional override for the default fiscal year settings. If omitted, the system uses defaults (Jan–Dec, 12 periods). | See **FiscalYearSettingDto** fields below |

##### FiscalYearSettingDto Fields

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `fiscalStartMonth` | int | ✅ | 1–12 | Month the fiscal year starts (1 = January) |
| `fiscalStartDay` | int | ✅ | 1–31 | Day of the month the fiscal year starts |
| `fiscalEndMonth` | int | ✅ | 1–12 | Month the fiscal year ends |
| `fiscalEndDay` | int | ✅ | 1–31 | Day of the month the fiscal year ends |
| `currentFiscalYear` | int | ✅ | — | The current fiscal year number (e.g., 2026) |
| `periodsPerYear` | int | ✅ | 1–52 | Number of accounting periods per year (typically 12 for monthly) |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Entity approved and seeded",
  "data": {
    "id": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityCode": "TEST01",
    "country": "US",
    "baseCurrency": "USD",
    "approvalStatus": "APPROVED",
    "status": "ACTIVE",
    "fiscalYearSetting": {
      "id": "00000000-0000-0000-0000-000000000030",
      "fiscalStartMonth": 1,
      "fiscalStartDay": 1,
      "fiscalEndMonth": 12,
      "fiscalEndDay": 31,
      "currentFiscalYear": 2026,
      "periodsPerYear": 12
    },
    "chartOfAccounts": [],
    "bankAccounts": [],
    "createdAt": "2026-05-20T10:00:00",
    "updatedAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

> **Important:** After approval, funding accounts are seeded in the `fa_accounts` table. Use [List Accounts](#41-list-accounts) to retrieve their UUIDs for use in capital injection requests.

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Entity is not in `PENDING` state |
| 404 | Entity not found |
| 500 | Unexpected server error |

---

### 1.6 Reject Entity

Transitions a `PENDING` entity to `REJECTED` status with a mandatory rejection reason.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/reject`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID |

#### Request Body

```json
{
  "reason": "Incomplete documentation"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `reason` | string | ✅ | max 500 chars | The reason for rejecting the entity registration |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Entity rejected",
  "data": {
    "id": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityCode": "TEST01",
    "country": "US",
    "baseCurrency": "USD",
    "approvalStatus": "REJECTED",
    "status": "ACTIVE",
    "createdAt": "2026-05-20T10:00:00",
    "updatedAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Entity is not in `PENDING` state |
| 404 | Entity not found |
| 500 | Unexpected server error |

---

### 1.7 List Accessible Entities

Returns the legal entities the current authenticated user has active access to. Drives the entity selector dropdown in the UI.

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/accessible`
- **Auth:** `isAuthenticated()`
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

Lists all user access grants for a given legal entity (admin view).

- **Method:** `GET`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:read`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID |

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

Grants a user access to a legal entity with a specified role.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/{id}/access`
- **Auth:** `users:write`
- **Status:** `201 Created`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Legal entity UUID |

#### Request Body

```json
{
  "authUserId": "00000000-0000-0000-0000-000000000002",
  "entityRole": "VIEWER"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `authUserId` | UUID | ✅ | — | The UUID of the user to grant access to | Retrieved from the auth/identity service (af-authhub) |
| `entityRole` | string | ✅ | Must be one of: `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` | The role to assign to the user for this entity | See **EntityRole** values below |

##### EntityRole Values

| Value | Description |
|-------|-------------|
| `VIEWER` | Read-only access to entity data |
| `EDITOR` | Can create and edit transactions |
| `APPROVER` | Can approve/reject transactions |
| `ADMIN` | Full administrative access including user management |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Access granted",
  "data": {
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
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 404 | Entity or shadow user not found |
| 409 | Access already exists for this user and entity |
| 500 | Unexpected server error |

---

### 1.10 Update Access Role

Updates the role of an existing user access grant for a legal entity.

- **Method:** `PATCH`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}/role`
- **Auth:** `users:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `entityId` | UUID | Legal entity UUID |
| `accessId` | UUID | Access grant UUID (retrieved from [List Access Grants](#18-list-access-grants)) |

#### Request Body

```json
{
  "authUserId": "00000000-0000-0000-0000-000000000002",
  "entityRole": "ADMIN"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `authUserId` | UUID | ✅ | — | The UUID of the user whose role is being updated |
| `entityRole` | string | ✅ | `VIEWER`, `EDITOR`, `APPROVER`, `ADMIN` | The new role to assign |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Access role updated",
  "data": {
    "id": "00000000-0000-0000-0000-000000000020",
    "authUserId": "00000000-0000-0000-0000-000000000002",
    "email": "john.doe@example.com",
    "displayName": "John Doe",
    "legalEntityId": "00000000-0000-0000-0000-000000000010",
    "entityName": "Test Entity",
    "entityRole": "ADMIN",
    "status": "ACTIVE",
    "lastAccessedAt": null,
    "createdAt": "2026-05-20T10:00:00"
  },
  "timestamp": "2026-05-20T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 404 | Access grant not found |
| 500 | Unexpected server error |

---

### 1.11 Revoke Access

Revokes a user's access to a legal entity (soft-delete by setting status to `INACTIVE`).

- **Method:** `DELETE`
- **Path:** `/api/v1/legal-entities/{entityId}/access/{accessId}`
- **Auth:** `users:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `entityId` | UUID | Legal entity UUID |
| `accessId` | UUID | Access grant UUID |

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
| 500 | Unexpected server error |

---

### 1.12 Select Entity Context

Records an entity context switch for the current user and returns the active context.

- **Method:** `POST`
- **Path:** `/api/v1/legal-entities/context/select`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Request Body

```json
{
  "legalEntityId": "00000000-0000-0000-0000-000000000010"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `legalEntityId` | UUID | ✅ | — | The UUID of the entity to switch context to | Retrieved from [List Entities](#12-list-entities) or [List Accessible Entities](#17-list-accessible-entities) |

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
| 400 | Validation failure |
| 403 | User does not have active access to this entity |
| 404 | Entity not found |
| 500 | Unexpected server error |

---

## 2. Capital Injection / Funding

**Base path:** `/api/v1/finance/funding`

---

### 2.1 Create Capital Injection

Records a new capital injection with double-entry ledger postings. For inter-entity transfers (`INTER_ENTITY_TRANSFER`), postings are written across both entity ledgers. USD conversion is automatic for non-USD entities.

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
  "manual_exchange_rate": 4282.0195921464765,
  "manual_rate_justification": "string",
  "manual_rate_approved_by": "string"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `target_entity_code` | string | ✅ | 2–10 chars | The entity code of the **receiving** legal entity (the one receiving the funds). This must match the `entityCode` of an approved entity. | Retrieved from [List Entities](#12-list-entities) response → `entityCode` field |
| `funding_source` | enum | ✅ | — | The source/category of the funding. Determines which source account role is used for the debit leg of the journal entry. | See **FundingSource** enum below |
| `amount` | number | ✅ | 0.01 – 999,999,999,999,999.9999 (15 integer, 4 decimal digits) | The amount in the **target entity's local currency**. The currency is auto-derived from the target entity's `baseCurrency`. | User-defined |
| `funding_date` | date | ✅ | Must be past or present (`yyyy-MM-dd`) | The date the funds were received. Used for exchange rate lookup (LLR-FIN-02.3). | User-defined |
| `source_account_id` | UUID | ✅ | — | The UUID of the **source funding account** (the account that funds are coming FROM). This must be an `ACTIVE` account belonging to the target entity (or source entity for inter-entity transfers). | Retrieved from [List Accounts](#41-list-accounts) — filter by `accountRole` matching the `funding_source` (see mapping table below) |
| `destination_account_id` | UUID | ❌ | — | The UUID of the **destination funding account** (the account receiving the funds). If omitted, the system auto-resolves to the entity's default `BANK_OPERATING` or `CASH` account. | Retrieved from [List Accounts](#41-list-accounts) — filter by `accountRole: "BANK_OPERATING"` or `"CASH"` |
| `source_entity_code` | string | ❌ | 2–10 chars | **Required only for `INTER_ENTITY_TRANSFER`.** The entity code of the **sending** entity. | Retrieved from [List Entities](#12-list-entities) response → `entityCode` of the sending entity |
| `referenceNumber` | string | ❌ | max 50 chars | An external reference or voucher number (e.g., "VCH-2026-001") | User-defined |
| `notes` | string | ❌ | max 500 chars | Free-text notes for this transaction | User-defined |
| `manual_exchange_rate` | number | ❌ | Positive value, max 10 integer + 6 decimal digits | A manually provided exchange rate (from target currency to USD). **Required when no automated rate exists** in the exchange rate table for the funding date. | User-defined (e.g., from a bank statement or central bank rate) |
| `manual_rate_justification` | string | ❌ | max 500 chars | Justification note explaining why a manual rate was used. **Required when `manual_exchange_rate` is supplied.** | User-defined |
| `manual_rate_approved_by` | string | ❌ | max 100 chars | Name of the person who approved the manual rate. **Required when `manual_exchange_rate` is supplied.** | User-defined |

##### FundingSource Enum Values

| Value | Description | Source Account Role Resolved | When to Use |
|-------|-------------|------------------------------|-------------|
| `FOUNDER_EQUITY` | Capital contributed directly by the company's founders | `FOUNDER_EQUITY` | When founders are injecting their own capital into the entity |
| `LOAN` | External loan proceeds deposited into the entity | `LOAN_PAYABLE` | When the entity receives loan disbursement from a bank or lender |
| `GRANT` | Grant income received from a government or external body | `GRANT_INCOME` | When the entity receives grant funding |
| `INTER_ENTITY_TRANSFER` | Cash transferred from another legal entity within the same group | `INTER_ENTITY_RECEIVABLE` (sending) / `INTER_ENTITY_PAYABLE` (receiving) | When moving funds between entities (e.g., US → India). Triggers a four-legged inter-entity journal entry. |

##### FundingSource → AccountRole Mapping

| FundingSource | Source AccountRole | Destination AccountRole |
|---------------|-------------------|------------------------|
| `FOUNDER_EQUITY` | `FOUNDER_EQUITY` | `BANK_OPERATING` (or `CASH` as fallback) |
| `LOAN` | `LOAN_PAYABLE` | `BANK_OPERATING` (or `CASH` as fallback) |
| `GRANT` | `GRANT_INCOME` | `BANK_OPERATING` (or `CASH` as fallback) |
| `INTER_ENTITY_TRANSFER` | `INTER_ENTITY_RECEIVABLE` (sending entity) | `INTER_ENTITY_PAYABLE` (receiving entity) |

> **How to find the correct `source_account_id`:**
> 1. Call `GET /api/v1/finance/accounts` to list all funding accounts
> 2. Filter the response by `legalEntityId` matching your target entity
> 3. For `FOUNDER_EQUITY`, look for an account with `accountRole: "FOUNDER_EQUITY"`
> 4. For `LOAN`, look for `accountRole: "LOAN_PAYABLE"`
> 5. For `GRANT`, look for `accountRole: "GRANT_INCOME"`
> 6. Copy the `id` (UUID) of that account as your `source_account_id`

> **How to find the correct `destination_account_id`:**
> 1. Call `GET /api/v1/finance/accounts` to list all funding accounts
> 2. Filter by `legalEntityId` matching your target entity
> 3. Look for an account with `accountRole: "BANK_OPERATING"` (preferred) or `"CASH"`
> 4. Copy the `id` (UUID) of that account as your `destination_account_id`
> 5. If omitted, the system auto-resolves to the entity's default `BANK_OPERATING` or `CASH` account

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

### 2.2 List Capital Injections

Lists all capital injections for a given entity (paginated).

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/capital-injections`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `entity_code` | string | ✅ | — | Target entity code (e.g., "INDIA", "NEPUYT") |
| `page` | int | ❌ | `0` | Zero-based page index |
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
        "capitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
        "targetEntityCode": "INDIA",
        "targetEntityName": "India Operations",
        "sourceEntityCode": null,
        "fundingSource": "FOUNDER_EQUITY",
        "amountLocal": 100000.0000,
        "currencyLocal": "INR",
        "amountUsd": 1200.0000,
        "fundingDate": "2026-05-18",
        "exchangeRateUsed": 0.012000,
        "rateSource": "API",
        "injectionStatus": "POSTED",
        "referenceNumber": "VCH-2026-001",
        "createdBy": "admin@example.com",
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

---

### 2.3 Get Capital Injection Detail

Returns full detail for a single capital injection, including its associated ledger entries.

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/capital-injections/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Capital injection ID (retrieved from [List Capital Injections](#22-list-capital-injections) or [Create Capital Injection](#21-create-capital-injection) response) |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Record retrieved successfully",
  "data": {
    "capitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
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
        "amountUsd": 600.0000,
        "description": "Inter-entity transfer out"
      }
    ]
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Capital injection not found |

---

### 2.4 Update Capital Injection Status

Updates the lifecycle status of a capital injection (e.g., VOID).

- **Method:** `PATCH`
- **Path:** `/api/v1/finance/funding/capital-injections/{id}/status`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Capital injection UUID |

#### Request Body

```json
{
  "injection_status": "VOID",
  "reason": "Duplicate entry, original was voided"
}
```

| Field | Type | Required | Constraints | Description | How to Retrieve |
|-------|------|----------|-------------|-------------|-----------------|
| `injection_status` | enum | ✅ | — | The new lifecycle status for the capital injection | See **CapitalInjectionStatus** enum below |
| `reason` | string | ❌ | max 500 chars | Reason for the status change. **Required when status is `VOID` or `FAILED`.** | User-defined |

##### CapitalInjectionStatus Enum Values

| Value | Description |
|-------|-------------|
| `POSTED` | Successfully persisted with balanced ledger entries |
| `PENDING_REVIEW` | Flagged for manual review (e.g., FX rate anomaly) |
| `FAILED` | Posting failed after header was created (rare) |
| `VOID` | Voided after posting; compensating reversal required |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Capital injection status updated",
  "data": null,
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Reason required for VOID status |
| 404 | Capital injection not found |

---

### 2.5 Get Inter-Entity Transfer Detail

Returns reconciliation details for an inter-entity transfer identified by its shared transfer ID.

- **Method:** `GET`
- **Path:** `/api/v1/finance/funding/inter-entity-transfers/{transferId}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `transferId` | UUID | Shared inter-entity transfer ID (retrieved from [Create Capital Injection](#21-create-capital-injection) response when `funding_source` is `INTER_ENTITY_TRANSFER`) |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": {
    "transferId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
    "sourceEntityCode": "US",
    "targetEntityCode": "INDIA",
    "sourceCapitalInjectionId": "550e8400-e29b-41d4-a716-446655440100",
    "targetCapitalInjectionId": "550e8400-e29b-41d4-a716-446655440200",
    "sourceJournalId": "550e8400-e29b-41d4-a716-446655440100",
    "targetJournalId": "550e8400-e29b-41d4-a716-446655440200"
  },
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Transfer ID not found |

---

## 3. Exchange Rates

**Base path:** `/api/v1/finance/exchange-rates`

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
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Exchange-rate UUID |

#### Response Body (200)

Returns a single `ExchangeRateSummaryResponse` object.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Exchange rate not found |

---

### 3.3 Create Exchange Rate (Admin)

Creates a new exchange rate record. Used by finance admins to manually enter rates
(e.g., from central bank publications) or to pre-load rates before processing
capital injections.

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
  "exchange_rate": 0.012000,
  "rate_source": "MANUAL",
  "created_by": "finance.admin@example.com",
  "approved_by": "finance.manager@example.com"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `source_currency` | string | ✅ | Exactly 3 chars, ISO 4217 | The source currency code (e.g., "INR", "NPR") |
| `target_currency` | string | ✅ | Exactly 3 chars, ISO 4217 | The target currency code (e.g., "USD") |
| `rate_date` | date | ✅ | Must be past or present (`yyyy-MM-dd`) | The date this rate is effective for |
| `exchange_rate` | number | ✅ | Positive, max 10 integer + 6 decimal digits | The conversion rate from source to target |
| `rate_source` | enum | ✅ | `API` or `MANUAL` | How this rate was obtained |
| `created_by` | string | ❌ | max 100 chars | User or system that submitted this rate |
| `approved_by` | string | ❌ | max 100 chars | Approver name (required for `MANUAL` rates) |

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
    "exchangeRate": 0.012000,
    "rateSource": "MANUAL",
    "createdAt": "2026-05-22T10:00:00"
  },
  "timestamp": "2026-05-22T10:00:00.123Z"
}
```

#### Error Responses

| Code | Condition |
|------|-----------|
| 400 | Validation failure |
| 409 | Rate already exists for this currency pair and date |
| 500 | Unexpected server error |

---

### 3.4 Daily Exchange Rate Sync Scheduler

The system includes a scheduled job (`ExchangeRateSyncScheduler`) that automatically
fetches exchange rates from an external API for all active non-USD entities.

#### How It Works

1. **Discovery:** The scheduler queries all active, approved legal entities and
   collects their distinct base currencies (e.g., INR, NPR).
2. **Fetch:** For each currency pair (e.g., INR→USD, NPR→USD), it calls the
   external exchange rate API to get today's rate.
3. **Persist:** The rate is saved to the `fa_exchange_rates` table with
   `rateSource = "API"`.
4. **Outbox Event:** A `EXCHANGE_RATE_SYNC_COMPLETED` outbox event is written
   to the `exchange_rate_outbox_events` table in the same transaction
   (Transactional Outbox Pattern).

#### Retry & Dead Letter Queue Strategy

| Attempt | Backoff | Action on Failure |
|---------|---------|-------------------|
| 1 | — | Retry after 30 seconds |
| 2 | 30s | Retry after 2 minutes |
| 3 | 2min | Move to Dead Letter Queue |
| DLQ | — | Publish `EXCHANGE_RATE_SYNC_FAILED` outbox event for operator inspection |

#### Configuration

```yaml
finance:
  funding:
    exchange-rate-sync-cron: ""  # e.g., "0 0 8 * * ?" for daily at 8 AM
```

> **Note:** The cron expression is intentionally left empty (`""`) until an
> external exchange rate API is integrated. Once integrated, uncomment the
> `@Scheduled` annotation in `ExchangeRateSyncScheduler` and set the cron
> expression.

#### Outbox Events

| Event Type | Payload | Purpose |
|-----------|---------|---------|
| `EXCHANGE_RATE_SYNC_COMPLETED` | `{ rateId, sourceCurrency, targetCurrency, rateDate, exchangeRate, rateSource }` | Published on successful sync |
| `EXCHANGE_RATE_SYNC_FAILED` | `{ sourceCurrency, targetCurrency, rateDate, error, retryCount }` | Published when all retries exhausted (DLQ) |
| `EXCHANGE_RATE_MANUALLY_UPDATED` | Reserved for future use | Published when admin creates/updates rate via API |

---

## 4. Funding Accounts

**Base path:** `/api/v1/finance/accounts`

> **Important:** Funding accounts (`fa_accounts` table) are **automatically seeded** when a legal entity is approved via [Approve Entity](#15-approve-entity). Each entity gets the following accounts based on its country template:

| AccountRole | AccountType | Purpose | Used As |
|-------------|-------------|---------|---------|
| `BANK_OPERATING` | `ASSET` | Primary operating bank account | Default destination for capital injections |
| `CASH` | `ASSET` | Petty-cash / physical cash on hand | Alternative destination for capital injections |
| `FOUNDER_EQUITY` | `EQUITY` | Equity injected by founders | Source account for `FOUNDER_EQUITY` funding |
| `LOAN_PAYABLE` | `LIABILITY` | Loan liability | Source account for `LOAN` funding |
| `GRANT_INCOME` | `REVENUE` | Grant income | Source account for `GRANT` funding |

### 4.1 List Accounts

Retrieve all funding accounts.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts`
- **Auth:** `organizations:write`
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
      "accountRole": "FOUNDER_EQUITY",
      "accountType": "EQUITY",
      "currencyCode": "USD",
      "status": "ACTIVE",
      "createdAt": "2026-05-18T10:00:00"
    }
  ],
  "timestamp": "2026-05-18T14:30:45.123Z"
}
```

##### AccountRole Enum Values

| Value | AccountType | Description |
|-------|-------------|-------------|
| `CASH` | `ASSET` | Petty-cash / physical cash on hand. Destination for cash injections. |
| `BANK_OPERATING` | `ASSET` | Primary operating bank account. Default injection destination. |
| `FOUNDER_EQUITY` | `EQUITY` | Equity injected by founders. Source account for `FOUNDER_EQUITY` funding. |
| `LOAN_PAYABLE` | `LIABILITY` | Loan liability account used when the funding source is a loan. |
| `GRANT_INCOME` | `REVENUE` | Income account used when the funding source is a grant. |
| `INTER_ENTITY_RECEIVABLE` | `ASSET` | Asset account on the sending entity recording amount owed from another entity. |
| `INTER_ENTITY_PAYABLE` | `LIABILITY` | Liability account on the receiving entity recording amount owed to the sending entity. |

##### AccountType Enum Values

| Value | Description |
|-------|-------------|
| `ASSET` | Resources owned by the entity |
| `LIABILITY` | Obligations owed by the entity |
| `EQUITY` | Owner's equity / capital |
| `REVENUE` | Income earned by the entity |
| `EXPENSE` | Costs incurred by the entity |

---

### 4.2 Get Account by ID

Retrieve one funding account by UUID.

- **Method:** `GET`
- **Path:** `/api/v1/finance/accounts/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Path Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Account UUID (retrieved from [List Accounts](#41-list-accounts)) |

#### Response Body (200)

Returns a single `AccountSummaryResponse` object.

#### Error Responses

| Code | Condition |
|------|-----------|
| 404 | Account not found |

---

## 5. Common Error Response Shapes

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
