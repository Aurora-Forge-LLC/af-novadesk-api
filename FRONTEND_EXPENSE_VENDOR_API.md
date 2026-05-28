# Finance Module — Expense & Vendor API Reference

Frontend integration guide for the **Vendor Management** and **Expense Transaction** endpoints of the NovaDesk Finance API.

---

## Base URL

| Environment | URL |
|-------------|-----|
| Local (same machine) | `http://localhost:8081/novadesk-api` |
| Local (LAN / other device) | `http://<server-lan-ip>:8081/novadesk-api` |

---

## Authentication

All endpoints require a valid JWT bearer token issued by **af-authhub**.

```http
Authorization: Bearer <access_token>
```

Include this header on every request. A missing or invalid token returns `401 Unauthorized`.

---

## Response Envelope

Every successful response is wrapped in a consistent envelope:

```json
{
  "status_code": 200,
  "message": "Human-readable description",
  "content": { }
}
```

For paginated list responses the `content` object contains:

```json
{
  "status_code": 200,
  "message": "Vendors retrieved successfully",
  "content": {
    "items": [ ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

---

## Error Responses (RFC 9457 Problem Detail)

All errors follow the Problem Detail format:

```json
{
  "type": "urn:af:novadesk:error:validation",
  "title": "Validation Failed",
  "status": 400,
  "detail": "vendorName: Vendor name is required; vendorType: Vendor type is required",
  "timestamp": "2026-05-27T10:43:16.618Z"
}
```

| `type` URN | Status | When |
|---|---|---|
| `urn:af:novadesk:error:bad-request` | 400 | Malformed JSON, bad path variable |
| `urn:af:novadesk:error:validation` | 400 | Bean validation failure |
| `urn:af:novadesk:error:not-found` | 404 | Resource does not exist |
| `urn:af:novadesk:error:duplicate` | 409 | Unique constraint violation |
| `urn:af:novadesk:error:unprocessable` | 422 | Invalid state transition (e.g. voiding an already-void expense) |
| `urn:af:novadesk:error:missing-exchange-rate` | 422 | No exchange rate available for the entity's currency |
| `urn:af:novadesk:error:internal` | 500 | Unexpected server error |

---

## Required Permissions

Each endpoint is guarded by a permission that must be present in the JWT `permissions` claim array.

| Action | Permission |
|--------|-----------|
| Create vendor | `VENDOR_CREATE` |
| Read vendors | `VENDOR_READ` |
| Update vendor / status | `VENDOR_UPDATE` |
| Record expense | `EXPENSE_CREATE` |
| Read expenses | `EXPENSE_READ` |
| Void expense | `EXPENSE_VOID` |
| View / download attachments | `VIEW_FINANCIAL_DOCUMENTS` |
| Upload / delete attachments | `EXPENSE_CREATE` |

---

## Enumerations

### `VendorType`
```
SERVICE_PROVIDER  — SaaS, cloud, professional services (e.g. AWS, Jira)
LANDLORD          — Office / facility landlord
UTILITY           — Electricity, water, internet, phone
SUPPLIER          — Goods or raw material suppliers
CONTRACTOR        — Independent contractors / freelancers
GOVERNMENT        — Tax bodies, regulatory agencies
OTHER             — Anything else
```

### `PaymentMethod`
```
BANK_TRANSFER
CREDIT_CARD
CASH
CHECK
```

### `ExpenseTransactionStatus`
```
POSTED  — Active, ledger entries exist
VOID    — Cancelled, reversal entries posted; immutable
```

### `Status` (vendor operational state)
```
ACTIVE    — Visible on the expense form autocomplete
INACTIVE  — Hidden from autocomplete; cannot be selected for new expenses
```

---

## Vendor API

Base path: `/api/v1/expense/vendors`

---

### Create Vendor

```http
POST /api/v1/expense/vendors
Content-Type: application/json
Authorization: Bearer <token>
```

**Request body:**

```json
{
  "vendorName": "Amazon Web Services",
  "vendorType": "SERVICE_PROVIDER",
  "taxId": "92-0070768",
  "defaultAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `vendorName` | string | ✅ | Max 100 chars. Must be unique within the organization. |
| `vendorType` | `VendorType` | ✅ | See enum above. |
| `taxId` | string | ❌ | Max 50 chars. Government-issued tax / company registration number. |
| `defaultAccountId` | UUID | ❌ | Pre-fills the destination account on the expense form. |

**Response `201 Created`:**

```json
{
  "status_code": 201,
  "message": "Vendor created successfully",
  "content": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "ACTIVE",
    "createdAt": "2026-05-27T10:43:16",
    "updatedAt": "2026-05-27T10:43:16",
    "vendorName": "Amazon Web Services",
    "vendorType": "SERVICE_PROVIDER",
    "taxId": "92-0070768",
    "defaultAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
  }
}
```

| Error | Cause |
|-------|-------|
| `400` | Missing required fields or validation failure |
| `409` | A vendor with the same name already exists in the organization |

---

### List Vendors

```http
GET /api/v1/expense/vendors?page=0&size=20&sortBy=vendorName&search=amazon
Authorization: Bearer <token>
```

| Query param | Default | Description |
|-------------|---------|-------------|
| `page` | `0` | Page number (0-based) |
| `size` | `20` | Records per page |
| `sortBy` | `vendorName` | Sort field |
| `search` | — | Name fragment for autocomplete filtering (optional) |

**Response `200 OK`:** paginated envelope with `VendorDto` items.

> Use `search` to power the vendor autocomplete dropdown on the expense form — it filters by name fragment server-side.

---

### Get Vendor by ID

```http
GET /api/v1/expense/vendors/{id}
Authorization: Bearer <token>
```

**Response `200 OK`:** single `VendorDto` in `content`.

| Error | Cause |
|-------|-------|
| `404` | Vendor UUID not found |

---

### Update Vendor

```http
PATCH /api/v1/expense/vendors/{id}
Content-Type: application/json
Authorization: Bearer <token>
```

Send only the fields you want to change. `vendorName` and `vendorType` are still required by validation — include the existing values if not changing them.

**Request body:** same shape as Create Vendor.

**Response `200 OK`:** updated `VendorDto` in `content`.

| Error | Cause |
|-------|-------|
| `400` | Validation failure |
| `404` | Vendor not found |
| `409` | New name already taken by another vendor |

---

### Update Vendor Status

Activate or deactivate a vendor. Inactive vendors are hidden from the expense form autocomplete.

```http
PATCH /api/v1/expense/vendors/{id}/status
Content-Type: application/json
Authorization: Bearer <token>
```

**Request body:**

```json
{
  "status": "INACTIVE"
}
```

| Field | Type | Required | Values |
|-------|------|----------|--------|
| `status` | `Status` | ✅ | `ACTIVE` or `INACTIVE` |

**Response `200 OK`:** updated `VendorDto` in `content`.

---

## Expense Transaction API

Base path: `/api/v1/expense/transactions`

Each recorded expense automatically creates two balanced ledger entries (double-entry bookkeeping):
- **CREDIT** on `sourceAccountId` — funds leave this account (e.g. Company Bank Account)
- **DEBIT** on `destinationAccountId` — expense is recognised here (e.g. Cloud Infrastructure Expense)

---

### Record Expense

```http
POST /api/v1/expense/transactions
Content-Type: application/json
Authorization: Bearer <token>
```

**Request body:**

```json
{
  "legalEntityId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "vendorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "expenseDate": "2026-05-22",
  "amount": "500.00",
  "paymentMethod": "BANK_TRANSFER",
  "sourceAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "destinationAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "invoiceReceiptNumber": "INV-2026-00123",
  "description": "Monthly server bill - May 2026"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `legalEntityId` | UUID | ✅ | Must belong to caller's organization |
| `vendorId` | UUID | ✅ | Must be an existing vendor |
| `expenseDate` | date (`YYYY-MM-DD`) | ✅ | Cannot be in the future |
| `amount` | string (numeric) | ✅ | Min `0.01`, max `999999999999999.9999`. Sent as string to preserve precision. |
| `paymentMethod` | `PaymentMethod` | ✅ | See enum above |
| `sourceAccountId` | UUID | ✅ | Account being **credited** (funds leave). Must ≠ `destinationAccountId`. |
| `destinationAccountId` | UUID | ✅ | Account being **debited** (expense recognised). Must ≠ `sourceAccountId`. |
| `invoiceReceiptNumber` | string | ❌ | Max 50 chars. Vendor invoice/receipt number for reconciliation. |
| `description` | string | ✅ | Max 500 chars. Appears in the general ledger. |

> ⚠️ `amount` must be serialized as a **string** (e.g. `"500.00"`) to avoid floating-point precision loss.

**Response `201 Created`:**

```json
{
  "status_code": 201,
  "message": "Expense recorded successfully",
  "content": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "transactionStatus": "POSTED",
    "status": "ACTIVE",
    "currencyCode": "USD",
    "createdByUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "attachments": [],
    "createdAt": "2026-05-27T10:43:16",
    "updatedAt": "2026-05-27T10:43:16",
    "legalEntityId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "vendorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "expenseDate": "2026-05-22",
    "amount": "500.0000",
    "paymentMethod": "BANK_TRANSFER",
    "sourceAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "destinationAccountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "invoiceReceiptNumber": "INV-2026-00123",
    "description": "Monthly server bill - May 2026"
  }
}
```

| Error | Cause |
|-------|-------|
| `400` | Validation failure, or `sourceAccountId == destinationAccountId` |
| `404` | Vendor or account UUID not found |
| `422` | No exchange rate available for the entity's currency |

---

### List Expenses

```http
GET /api/v1/expense/transactions?page=0&size=20&sortBy=expenseDate&status=POSTED
Authorization: Bearer <token>
```

| Query param | Default | Description |
|-------------|---------|-------------|
| `page` | `0` | Page number (0-based) |
| `size` | `20` | Records per page |
| `sortBy` | `expenseDate` | Sort field |
| `status` | — | Filter by `POSTED` or `VOID` (optional — omit for all) |

**Response `200 OK`:** paginated envelope with `ExpenseTransactionDto` items.

---

### Get Expense by ID

```http
GET /api/v1/expense/transactions/{id}
Authorization: Bearer <token>
```

**Response `200 OK`:** single `ExpenseTransactionDto` in `content`, including the `attachments` array.

| Error | Cause |
|-------|-------|
| `404` | Expense UUID not found |

---

### Void Expense

Cancels a `POSTED` expense. Creates two offsetting reversal ledger entries and marks the transaction as `VOID`. **A voided transaction is permanently immutable.**

```http
POST /api/v1/expense/transactions/{id}/void
Content-Type: application/json
Authorization: Bearer <token>
```

**Request body:**

```json
{
  "voidReason": "Duplicate entry — original recorded under transaction #TXN-2026-00045"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `voidReason` | string | ✅ | Max 500 chars. Recorded in audit trail. |

**Response `200 OK`:** updated `ExpenseTransactionDto` with `transactionStatus: "VOID"`.

| Error | Cause |
|-------|-------|
| `404` | Expense not found |
| `422` | Transaction is already `VOID` |

---

## Attachment API

Attachments are scoped to a specific expense transaction.

Supported file types: **PDF, PNG, JPG, JPEG** — max **5 MB**.

Files are **encrypted at rest** in object storage. Access is via short-lived pre-signed URLs (15-minute expiry) returned in the `downloadUrl` field.

---

### Upload Attachment

```http
POST /api/v1/expense/transactions/{id}/attachments
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

| Form field | Type | Required |
|------------|------|----------|
| `file` | file | ✅ |

**Response `201 Created`:**

```json
{
  "status_code": 201,
  "message": "Attachment uploaded successfully",
  "content": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "expenseTransactionId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "originalFileName": "aws-invoice-may-2026.pdf",
    "fileType": "PDF",
    "fileSizeBytes": 204800,
    "downloadUrl": "https://storage.example.com/...?X-Amz-Expires=900",
    "isEncrypted": true,
    "uploadedByUserId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "createdAt": "2026-05-27T10:43:16"
  }
}
```

| Error | Cause |
|-------|-------|
| `400` | Unsupported file type, file exceeds 5 MB, or missing `file` form field |
| `404` | Expense transaction not found |

---

### List Attachments

```http
GET /api/v1/expense/transactions/{id}/attachments
Authorization: Bearer <token>
```

Returns metadata only. Use the `downloadUrl` from each item to retrieve the actual file. URLs expire after **15 minutes** — do not cache them.

**Response `200 OK`:** array of `ExpenseAttachmentDto` in `content`.

| Error | Cause |
|-------|-------|
| `404` | Expense transaction not found |

---

### Delete Attachment

```http
DELETE /api/v1/expense/transactions/{transactionId}/attachments/{attachmentId}
Authorization: Bearer <token>
```

Permanently removes the file from object storage and the database record. Only allowed on `POSTED` transactions.

**Response `204 No Content`**

| Error | Cause |
|-------|-------|
| `400` | Transaction is `VOID` — attachments on voided expenses cannot be deleted |
| `404` | Transaction or attachment UUID not found |

---

## Quick Reference

### Vendor endpoints

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| `POST` | `/api/v1/expense/vendors` | `VENDOR_CREATE` | Create vendor |
| `GET` | `/api/v1/expense/vendors` | `VENDOR_READ` | List vendors (paginated, searchable) |
| `GET` | `/api/v1/expense/vendors/{id}` | `VENDOR_READ` | Get vendor by ID |
| `PATCH` | `/api/v1/expense/vendors/{id}` | `VENDOR_UPDATE` | Update vendor details |
| `PATCH` | `/api/v1/expense/vendors/{id}/status` | `VENDOR_UPDATE` | Activate / deactivate vendor |

### Expense endpoints

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| `POST` | `/api/v1/expense/transactions` | `EXPENSE_CREATE` | Record expense + post ledger entries |
| `GET` | `/api/v1/expense/transactions` | `EXPENSE_READ` | List expenses (paginated, filterable) |
| `GET` | `/api/v1/expense/transactions/{id}` | `EXPENSE_READ` | Get expense + attachments by ID |
| `POST` | `/api/v1/expense/transactions/{id}/void` | `EXPENSE_VOID` | Void expense + post reversal entries |
| `POST` | `/api/v1/expense/transactions/{id}/attachments` | `EXPENSE_CREATE` | Upload attachment |
| `GET` | `/api/v1/expense/transactions/{id}/attachments` | `VIEW_FINANCIAL_DOCUMENTS` | List attachment metadata |
| `DELETE` | `/api/v1/expense/transactions/{transactionId}/attachments/{attachmentId}` | `EXPENSE_CREATE` | Delete attachment |

---

## Typical Frontend Flow

### Recording an expense

```
1. GET  /api/v1/expense/vendors?search=<user-input>
         → populate vendor autocomplete dropdown

2. GET  /api/v1/legal-entities            (separate module)
         → populate legal entity selector

3. GET  /api/v1/finance/accounts          (separate module)
         → populate source / destination account selectors

4. POST /api/v1/expense/transactions
         → submit expense form

5. POST /api/v1/expense/transactions/{id}/attachments   (optional)
         → attach invoice/receipt file(s)
```

### Viewing an expense with attachments

```
1. GET  /api/v1/expense/transactions/{id}
         → renders expense detail (attachments array is included)

2. Use downloadUrl from each attachment item to open/download the file
   (URL expires in 15 min — fetch a fresh list if the link has expired)
```
