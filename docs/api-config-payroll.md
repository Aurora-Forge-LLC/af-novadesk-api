# Payroll - Tax Configuration API

Base path: `/api/v1/payroll/tax-configurations`

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/payroll/tax-configurations` | List configs (all or by entity) |
| `GET` | `/api/v1/payroll/tax-configurations/{id}` | Get single config |
| `POST` | `/api/v1/payroll/tax-configurations` | Create config |
| `PUT` | `/api/v1/payroll/tax-configurations/{id}` | Update config |
| `DELETE` | `/api/v1/payroll/tax-configurations/{id}` | **Soft-delete** (sets `status=DELETED`) |
| `DELETE` | `/api/v1/payroll/tax-configurations/{id}/hard` | **Hard-delete** (removes from DB) |

---

## POST `/api/v1/payroll/tax-configurations`

Creates a tax configuration. `jurisdiction` is derived automatically from the legal entity's country.

### Request Body

```json
{
  "legal_entity_id": "7091a851-79a2-49b3-a0f8-6d0b25c45208",
  "tax_name": "Income Tax 2026",
  "effective_from": "2026-06-10",
  "effective_to": null,
  "ssf_employee_rate": 0.11,
  "ssf_employer_rate": 0.20,
  "ssf_max_cap_amount": 50000.0000,
  "pf_employee_rate": null,
  "pf_employer_rate": null,
  "pf_max_cap_amount": null,
  "professional_tax_amount": null,
  "professional_tax_state": null,
  "tax_slabs": [
    {
      "slab_order": 1,
      "income_from": 0.0000,
      "income_to": 500000.0000,
      "rate_percent": 1.0000,
      "is_annual": true,
      "description": "NPR 0 - 500,000: 1%"
    },
    {
      "slab_order": 2,
      "income_from": 500001.0000,
      "income_to": 700000.0000,
      "rate_percent": 10.0000,
      "is_annual": true,
      "description": "NPR 500,001 - 700,000: 10%"
    }
  ]
}
```

### Response (201 Created)

```json
{
  "success": true,
  "message": "Record created successfully",
  "data": {
    "id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "legal_entity_id": "7091a851-79a2-49b3-a0f8-6d0b25c45208",
    "legal_entity_name": "Nepal Subsidiary",
    "tax_name": "Income Tax 2026",
    "jurisdiction": "NEPAL",
    "ssf_employee_rate": 0.11,
    "ssf_employer_rate": 0.20,
    "ssf_max_cap_amount": 50000.0000,
    "pf_employee_rate": null,
    "pf_employer_rate": null,
    "pf_max_cap_amount": null,
    "professional_tax_amount": null,
    "professional_tax_state": null,
    "effective_from": "2026-06-10",
    "effective_to": null,
    "is_active": true,
    "last_modified_by_id": null,
    "last_modified_by_name": null,
    "tax_slabs": [
      {
        "id": "a1b2c3d4-...",
        "slab_order": 1,
        "income_from": 0.0000,
        "income_to": 500000.0000,
        "rate_percent": 1.0000,
        "is_annual": true,
        "description": "NPR 0 - 500,000: 1%",
        "created_at": "2026-06-10T10:00:00",
        "updated_at": "2026-06-10T10:00:00"
      },
      {
        "id": "e5f6g7h8-...",
        "slab_order": 2,
        "income_from": 500001.0000,
        "income_to": 700000.0000,
        "rate_percent": 10.0000,
        "is_annual": true,
        "description": "NPR 500,001 - 700,000: 10%",
        "created_at": "2026-06-10T10:00:00",
        "updated_at": "2026-06-10T10:00:00"
      }
    ],
    "created_at": "2026-06-10T10:00:00",
    "updated_at": "2026-06-10T10:00:00"
  }
}
```

---

## PUT `/api/v1/payroll/tax-configurations/{id}`

Updates an existing tax configuration.

### Request Body

Same structure as POST. Only provided fields are updated. `jurisdiction` is read-only.

### Response (200 OK)

Same structure as POST response.

---

## DELETE `/api/v1/payroll/tax-configurations/{id}` (Soft-delete)

Sets the record `status` to `DELETED` and `is_active` to `false`. The record remains in the database.

### Response (204 No Content)

```json
{
  "success": true,
  "message": "Record deleted successfully",
  "data": null
}
```

---

## DELETE `/api/v1/payroll/tax-configurations/{id}/hard` (Hard-delete)

Permanently removes the record and its child tax slabs from the database.

### Response (204 No Content)

```json
{
  "success": true,
  "message": "Record deleted successfully",
  "data": null
}
```

---

## GET `/api/v1/payroll/tax-configurations`

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `legal_entity_id` | UUID | No | Filter by legal entity. Omit to list all. |

### Response (200 OK)

Same structure as POST response, with `data` as an array.

---

## GET `/api/v1/payroll/tax-configurations/{id}`

### Response (200 OK)

Same structure as POST response, with a single record.

---

## Field Reference

### TaxConfiguration fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `legal_entity_id` | UUID | **Yes** | Legal entity this config applies to |
| `tax_name` | String(100) | No | Human-readable label, e.g. "Income Tax 2026" |
| `jurisdiction` | Enum | **Read-only** | Derived from legal entity's country (`NEPAL`/`INDIA`/`USA`) |
| `effective_from` | Date | **Yes** | Start of validity |
| `effective_to` | Date | No | End of validity (null = current) |
| `ssf_employee_rate` | Decimal(5,4) | No | SSF employee contribution rate (Nepal) |
| `ssf_employer_rate` | Decimal(5,4) | No | SSF employer contribution rate (Nepal) |
| `ssf_max_cap_amount` | Decimal(19,4) | No | SSF max gross salary cap |
| `pf_employee_rate` | Decimal(5,4) | No | PF employee contribution rate (India) |
| `pf_employer_rate` | Decimal(5,4) | No | PF employer contribution rate (India) |
| `pf_max_cap_amount` | Decimal(19,4) | No | PF max gross salary cap |
| `professional_tax_amount` | Decimal(19,4) | No | Professional tax fixed amount (India) |
| `professional_tax_state` | String(50) | No | State for professional tax, e.g. "Karnataka" |
| `is_active` | Boolean | No | Default: `true` |

### TaxSlab fields

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `slab_order` | Integer | **Yes** | Position (1, 2, 3...) |
| `income_from` | Decimal(19,4) | **Yes** | Lower bound (inclusive) |
| `income_to` | Decimal(19,4) | No | Upper bound (null = highest bracket) |
| `rate_percent` | Decimal(5,4) | **Yes** | Percentage value, e.g. `3.0000` for 3% |
| `is_annual` | Boolean | No | Default: `true` (annual slab ÷ 12 for monthly) |
| `description` | String(200) | No | e.g. "NPR 0 - 500,000: 1%" |
