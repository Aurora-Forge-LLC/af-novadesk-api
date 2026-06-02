# NovaDesk API — Payroll Module Endpoints

> **Base URL:** `{{baseUrl}}/novadesk-api`  
> **Auth:** All endpoints require a valid JWT bearer token (`Authorization: Bearer <token>`).  
> **Response Envelope:** Every response is wrapped in the standard `ApiResponse` envelope.

---

## Table of Contents

1. [Employees](#1-employees)
   - 1.1 [Onboard Employee](#11-onboard-employee)
   - 1.2 [List Employees by Entity](#12-list-employees-by-entity)
   - 1.3 [Get Employee by ID](#13-get-employee-by-id)
   - 1.4 [Update Employee](#14-update-employee)
   - 1.5 [Terminate Employee](#15-terminate-employee)
2. [Leave Requests](#2-leave-requests)
   - 2.1 [Submit Leave Request](#21-submit-leave-request)
   - 2.2 [Get Leave Request](#22-get-leave-request)
   - 2.3 [List My Leave Requests](#23-list-my-leave-requests)
   - 2.4 [List Pending Requests (Approver Dashboard)](#24-list-pending-requests-approver-dashboard)
   - 2.5 [Approve Leave Request](#25-approve-leave-request)
   - 2.6 [Reject Leave Request](#26-reject-leave-request)
   - 2.7 [Request Modification](#27-request-modification)
   - 2.8 [Cancel Leave Request](#28-cancel-leave-request)
   - 2.9 [Get Leave Balances](#29-get-leave-balances)
3. [Payroll Batches](#3-payroll-batches)
   - 3.1 [Initiate Payroll Batch](#31-initiate-payroll-batch)
   - 3.2 [Get Payroll Batch](#32-get-payroll-batch)
   - 3.3 [List Batches by Entity](#33-list-batches-by-entity)
   - 3.4 [Validate Attendance & Flag Employees](#34-validate-attendance--flag-employees)
   - 3.5 [List Flagged Employees](#35-list-flagged-employees)
   - 3.6 [Process Flagged Employee](#36-process-flagged-employee)
   - 3.7 [Calculate Salaries](#37-calculate-salaries)
   - 3.8 [Generate Payslips](#38-generate-payslips)
   - 3.9 [Approve Payroll](#39-approve-payroll)
   - 3.10 [Reject Payroll](#310-reject-payroll)
   - 3.11 [Void Payroll](#311-void-payroll)
   - 3.12 [Get Ledger Entries](#312-get-ledger-entries)
4. [Payslips](#4-payslips)
   - 4.1 [List My Payslips](#41-list-my-payslips)
   - 4.2 [Get Payslip Detail](#42-get-payslip-detail)
   - 4.3 [Download Payslip PDF](#43-download-payslip-pdf)
   - 4.4 [List Payslips by Batch](#44-list-payslips-by-batch)
5. [Tax Configuration](#5-tax-configuration)
   - 5.1 [List Tax Configurations by Entity](#51-list-tax-configurations-by-entity)
   - 5.2 [Get Tax Configuration](#52-get-tax-configuration)
   - 5.3 [Create Tax Configuration](#53-create-tax-configuration)
   - 5.4 [Update Tax Configuration](#54-update-tax-configuration)
6. [Endpoint Dependency Map](#6-endpoint-dependency-map)
7. [Common Error Response Shapes](#7-common-error-response-shapes)

---

## 1. Employees

**Base path:** `/api/v1/payroll/employees`

> **Prerequisite:** Before onboarding an employee, the following must exist:
> - A [`ShadowUser`](#) (AuthHub identity) — every employee must first be registered as a ShadowUser
> - A [`LegalEntity`](docs/api-endpoints-finance.md#11-create-entity) in `APPROVED` state — the entity the employee belongs to
> - *(Optional)* A [`FiscalYearSetting`](docs/api-endpoints-finance.md#15-approve-entity) for the entity — auto-created during entity approval; required for leave balance initialization

### 1.1 Onboard Employee

Creates an Employee record linked to a ShadowUser identity. Automatically creates three [`LeaveBalance`](#29-get-leave-balances) records (Paid=10 days, Sick=5 days, Unpaid=unlimited) for the current fiscal year. Emits `EMPLOYEE_ONBOARDED` audit log entry.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/employees`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "shadowUserId": "00000000-0000-0000-0000-000000000001",
  "legalEntityId": "00000000-0000-0000-0000-000000000010",
  "employeeCode": "EMP-001",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "department": "Engineering",
  "designation": "Senior Developer",
  "hireDate": "2026-01-15",
  "baseSalary": 150000.0000,
  "salaryCurrency": "NPR",
  "bankAccountNumber": "1234567890",
  "bankName": "Global IME Bank",
  "bankIfscCode": "GLBBNPKA",
  "managerId": "00000000-0000-0000-0000-000000000002"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `shadowUserId` | UUID | ✅ | Must exist in `shadow_users` table | AuthHub identity reference |
| `legalEntityId` | UUID | ✅ | Entity must be `APPROVED` | Legal entity the employee belongs to |
| `employeeCode` | string | ✅ | max 50 chars, unique per entity | Employee identifier (e.g., `EMP-001`) |
| `firstName` | string | ✅ | max 100 chars | First name (snapshotted from ShadowUser) |
| `lastName` | string | ✅ | max 100 chars | Last name (snapshotted from ShadowUser) |
| `email` | string | ❌ | max 255 chars | Email; defaults to ShadowUser email if omitted |
| `department` | string | ❌ | max 100 chars | Department name |
| `designation` | string | ❌ | max 100 chars | Job title |
| `hireDate` | date | ✅ | Past or present | Used as leave accrual start date |
| `baseSalary` | number | ✅ | > 0, 19,4 scale | Monthly base salary in entity's currency |
| `salaryCurrency` | string | ✅ | 3-char ISO 4217 | Currency code (e.g., `NPR`, `INR`) |
| `bankAccountNumber` | string | ❌ | max 50 chars | Bank account for salary disbursement |
| `bankName` | string | ❌ | max 150 chars | Bank name |
| `bankIfscCode` | string | ❌ | max 20 chars | IFSC (India) or SWIFT code |
| `managerId` | UUID | ❌ | Must be an existing Employee | Direct manager (self-referential FK) |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Employee onboarded successfully",
  "data": {
    "id": "00000000-0000-0000-0000-000000000003",
    "shadowUserId": "00000000-0000-0000-0000-000000000001",
    "authUserId": "00000000-0000-0000-0000-000000000001",
    "legalEntityId": "00000000-0000-0000-0000-000000000010",
    "legalEntityName": "Nepal Subsidiary",
    "employeeCode": "EMP-001",
    "firstName": "John",
    "lastName": "Doe",
    "displayName": "John Doe",
    "email": "john.doe@example.com",
    "department": "Engineering",
    "designation": "Senior Developer",
    "hireDate": "2026-01-15",
    "baseSalary": 150000.0000,
    "salaryCurrency": "NPR",
    "bankAccountNumber": "1234567890",
    "bankName": "Global IME Bank",
    "bankIfscCode": "GLBBNPKA",
    "managerId": "00000000-0000-0000-0000-000000000002",
    "managerName": "Jane Smith",
    "status": "ACTIVE",
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-06-01T10:00:00"
  },
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 400 | `VALIDATION_ERROR` | Request validation failed |
| 404 | — | Shadow user or legal entity not found |
| 409 | `PAY_EMP_002` | Employee already exists for this ShadowUser + Entity |

---

### 1.2 List Employees by Entity

Returns all employees within the specified legal entity.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/employees`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `legalEntityId` | UUID | ✅ | — | Legal entity to scope the query |

---

### 1.3 Get Employee by ID

Returns full detail for a single employee.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/employees/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | `PAY_EMP_001` | Employee not found: `{id}` |

---

### 1.4 Update Employee

Partially updates employee details. Only provided non-null fields are updated.

- **Method:** `PATCH`
- **Path:** `/api/v1/payroll/employees/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "department": "Platform Engineering",
  "designation": "Staff Engineer",
  "baseSalary": 180000.0000,
  "managerId": "00000000-0000-0000-0000-000000000004"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `department` | string | ❌ | max 100 chars | New department |
| `designation` | string | ❌ | max 100 chars | New job title |
| `baseSalary` | number | ❌ | > 0 | Updated base salary |
| `salaryCurrency` | string | ❌ | 3-char ISO 4217 | Updated currency |
| `bankAccountNumber` | string | ❌ | max 50 chars | Updated bank account |
| `bankName` | string | ❌ | max 150 chars | Updated bank name |
| `bankIfscCode` | string | ❌ | max 20 chars | Updated IFSC/SWIFT |
| `managerId` | UUID | ❌ | Must be existing Employee | Updated manager |

---

### 1.5 Terminate Employee

Sets the employee's termination date and marks them as `INACTIVE`. Emits `EMPLOYEE_TERMINATED` audit log entry.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/employees/{id}/terminate`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `terminationDate` | date | ✅ | Past or present | Date of termination |

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | `PAY_EMP_001` | Employee not found |

---

## 2. Leave Requests

**Base path:** `/api/v1/payroll/leaves`

> **Prerequisite:** The employee must have been [onboarded](#11-onboard-employee) (LeaveBalance records are auto-created during onboarding).

### 2.1 Submit Leave Request

Employee submits a new leave request. The system:
1. Validates dates (start ≥ today, end ≥ start)
2. Checks Paid/Sick leave balance availability
3. Auto-converts excess days to Unpaid if balance insufficient
4. Reserves requested days in the balance (`pendingDays`)
5. Sets the employee's direct manager as the approver
6. Emits `LEAVE_REQUESTED` outbox event

- **Method:** `POST`
- **Path:** `/api/v1/payroll/leaves/requests`
- **Auth:** `isAuthenticated()`
- **Status:** `201 Created`

#### Request Body

```json
{
  "employeeId": "00000000-0000-0000-0000-000000000003",
  "leaveType": "PAID",
  "startDate": "2026-06-10",
  "endDate": "2026-06-14",
  "numberOfDays": 5.0,
  "reason": "Family vacation",
  "attachmentPath": null
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `employeeId` | UUID | ✅ | Must exist | Employee requesting leave |
| `leaveType` | enum | ✅ | `PAID`, `SICK`, `UNPAID` | Type of leave |
| `startDate` | date | ✅ | Future or present | First day of leave |
| `endDate` | date | ✅ | ≥ startDate | Last day of leave |
| `numberOfDays` | number | ✅ | > 0, 1 decimal place | Duration (e.g., 1.5 for half-day) |
| `reason` | string | ❌ | max 500 chars; **required for SICK/UNPAID** | Reason for leave |
| `attachmentPath` | string | ❌ | max 500 chars | MinIO path for medical certificate (Sick leave) |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Leave request submitted",
  "data": {
    "id": "00000000-0000-0000-0000-000000000100",
    "employeeId": "00000000-0000-0000-0000-000000000003",
    "employeeName": "John Doe",
    "leaveType": "PAID",
    "startDate": "2026-06-10",
    "endDate": "2026-06-14",
    "numberOfDays": 5.0,
    "reason": "Family vacation",
    "paidBalanceBefore": 10.0,
    "sickBalanceBefore": 5.0,
    "paidDaysUsed": 5.0,
    "sickDaysUsed": 0.0,
    "unpaidDaysUsed": 0.0,
    "leaveRequestStatus": "PENDING",
    "approverId": "00000000-0000-0000-0000-000000000002",
    "approverName": "Jane Smith",
    "submittedAt": "2026-06-01T10:00:00",
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-06-01T10:00:00"
  },
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

> **Auto-Conversion Warning:** If `unpaidDaysUsed > 0`, the response includes `warningMessage`:  
> *"You have 2.0 days of PAID leave. 3.0 days will be marked as Unpaid."*

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 400 | `PAY_LR_003` | Start date cannot be in the past |
| 400 | `PAY_LR_003` | End date must be after start date |
| 404 | `PAY_EMP_001` | Employee not found |
| 422 | `PAY_LB_002` | Insufficient leave balance (auto-converted to Unpaid) |

---

### 2.2 Get Leave Request

Returns a single leave request by ID.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/leaves/requests/{id}`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

---

### 2.3 List My Leave Requests

Returns all leave requests for a given employee, ordered by creation date descending.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/leaves/requests`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `employeeId` | UUID | ✅ | — | Employee whose requests to list |

---

### 2.4 List Pending Requests (Approver Dashboard)

Returns all `PENDING` leave requests for a specific approver. Used by managers to review their team's pending leave requests.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/leaves/pending`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `approverId` | UUID | ✅ | Must be an Employee | The approver's employee ID |

---

### 2.5 Approve Leave Request

Transitions a leave request from `PENDING`/`MODIFICATION_REQUESTED` to `APPROVED`. The system:
1. Deducts used days from leave balances (`usedDays` ↑, `pendingDays` ↓)
2. Creates immutable [`LeaveTransaction`](#) records (DEDUCTION)
3. Emits `LEAVE_APPROVED` outbox event

> **Multi-Level Approval Rule (LLR-PAY-01.3):**
> - Leaves > 5 consecutive business days → requires second approver (HR)
> - Unpaid leaves → requires executive approver (Finance Manager+)

- **Method:** `POST`
- **Path:** `/api/v1/payroll/leaves/requests/{id}/approve`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Request Body

```json
{
  "approverComment": "Approved. Enjoy your vacation!"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `approverComment` | string | ❌ | max 500 chars | Optional approval comment |

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | `PAY_LR_001` | Leave request not found |
| 422 | `PAY_LR_002` | Cannot transition from current status to APPROVED |

---

### 2.6 Reject Leave Request

Transitions a leave request from `PENDING`/`MODIFICATION_REQUESTED` to `REJECTED`. Restores reserved `pendingDays`. A rejection reason is mandatory.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/leaves/requests/{id}/reject`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Request Body

```json
{
  "approverComment": "Critical sprint next week. Please reschedule."
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `approverComment` | string | ✅ | max 500 chars | Rejection reason (mandatory) |

---

### 2.7 Request Modification

Manager requests the employee to modify the leave dates. Status changes to `MODIFICATION_REQUESTED`. Employee can resubmit with new dates.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/leaves/requests/{id}/modify`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Request Body

```json
{
  "approverComment": "Can you take leave from June 15 instead?"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `approverComment` | string | ✅ | max 500 chars | Suggested alternate dates / modification reason |

---

### 2.8 Cancel Leave Request

Employee cancels a leave request. **Must be at least 2 calendar days before the start date.**
- If already `APPROVED`: restores used days to balance, creates RESTORATION [`LeaveTransaction`](#)
- If still `PENDING`/`MODIFICATION_REQUESTED`: releases reserved `pendingDays`
- Emits `LEAVE_CANCELLED` outbox event

- **Method:** `POST`
- **Path:** `/api/v1/payroll/leaves/requests/{id}/cancel`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 400 | `PAY_LR_003` | Leave can only be cancelled at least 2 days before start date |
| 404 | `PAY_LR_001` | Leave request not found |
| 422 | `PAY_LR_002` | Leave request is already cancelled |

---

### 2.9 Get Leave Balances

Returns all leave balances for a given employee (Paid, Sick, Unpaid) for the current fiscal year.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/leaves/balances`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `employeeId` | UUID | ✅ | — | Employee whose balances to retrieve |

#### Response Body (200)

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000200",
      "employeeId": "00000000-0000-0000-0000-000000000003",
      "employeeName": "John Doe",
      "leaveType": "PAID",
      "totalAllocated": 10.0,
      "usedDays": 5.0,
      "pendingDays": 1.0,
      "availableDays": 4.0,
      "accrualStartDate": "2026-01-15",
      "fiscalYearSettingId": "00000000-0000-0000-0000-000000000030"
    },
    {
      "leaveType": "SICK",
      "totalAllocated": 5.0,
      "usedDays": 0.0,
      "pendingDays": 0.0,
      "availableDays": 5.0
    },
    {
      "leaveType": "UNPAID",
      "totalAllocated": 999.0,
      "usedDays": 0.0,
      "pendingDays": 0.0,
      "availableDays": 999.0
    }
  ],
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

| Response Field | Type | Description |
|----------------|------|-------------|
| `totalAllocated` | number | Annual allocation (10 for Paid, 5 for Sick, 999 for Unpaid) |
| `usedDays` | number | Days already consumed (approved) |
| `pendingDays` | number | Days in pending leave requests (reserved) |
| `availableDays` | number | Computed: `totalAllocated − usedDays − pendingDays` |

---

## 3. Payroll Batches

**Base path:** `/api/v1/payroll/batches`

> **Prerequisite:** Before initiating a payroll batch:
> - At least one [`Employee`](#11-onboard-employee) must exist in the entity
> - A [`TaxConfiguration`](#53-create-tax-configuration) must exist for the entity's jurisdiction
> - All employees must have valid bank account details on file
> - All leave requests for the pay period must be in terminal state (`APPROVED` or `REJECTED`)

> **Payroll Batch State Machine:**
> ```
> INITIATED → UNDER_REVIEW → APPROVED → APPROVED_PENDING_PAYMENT
>                 ↓               ↓
>             REJECTED         VOIDED
> ```

### 3.1 Initiate Payroll Batch

Creates a new payroll batch for a legal entity and pay period. Performs pre-checks:
- Entity is `APPROVED`
- No existing batch for same entity + period
- Pending leave requests validated
- Emits `PAYROLL_INITIATED` outbox event

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body

```json
{
  "legalEntityId": "00000000-0000-0000-0000-000000000010",
  "payPeriodStart": "2026-05-01",
  "payPeriodEnd": "2026-05-31",
  "paymentDate": "2026-05-30",
  "currencyCode": "NPR"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `legalEntityId` | UUID | ✅ | Entity must be `APPROVED` | Entity to run payroll for |
| `payPeriodStart` | date | ✅ | First of month recommended | Start of pay period |
| `payPeriodEnd` | date | ✅ | ≥ payPeriodStart | End of pay period |
| `paymentDate` | date | ✅ | Usually last working day | Date salaries are paid |
| `currencyCode` | string | ❌ | 3-char ISO 4217; defaults to entity base currency | Payroll currency |

#### Response Body (201)

```json
{
  "success": true,
  "code": 201,
  "message": "Payroll batch initiated",
  "data": {
    "id": "00000000-0000-0000-0000-000000000300",
    "legalEntityId": "00000000-0000-0000-0000-000000000010",
    "legalEntityName": "Nepal Subsidiary",
    "payPeriodStart": "2026-05-01",
    "payPeriodEnd": "2026-05-31",
    "paymentDate": "2026-05-30",
    "currencyCode": "NPR",
    "batchStatus": "INITIATED",
    "totalHeadcount": 0,
    "processedCount": 0,
    "flaggedCount": 0,
    "totalGrossSalary": 0.0000,
    "totalDeductions": 0.0000,
    "totalNetPayout": 0.0000,
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-06-01T10:00:00"
  },
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | — | Legal entity not found |
| 409 | `PAY_PB_002` | Payroll batch already exists for this entity + period |
| 422 | `PAY_PB_005` | Pre-checks failed (pending leave requests, missing config) |

---

### 3.2 Get Payroll Batch

Returns full detail for a payroll batch including children (payslips, flagged employees, ledger entries).

- **Method:** `GET`
- **Path:** `/api/v1/payroll/batches/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

---

### 3.3 List Batches by Entity

Returns all payroll batches for a legal entity, ordered by creation date descending.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/batches`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `legalEntityId` | UUID | ✅ | — | Entity to scope the query |

---

### 3.4 Validate Attendance & Flag Employees

Scans all active employees in the entity for the pay period. Counts working days (excludes weekends), identifies employees with unpaid leave or unauthorized absences. Flagged employees are added to the "Requires Review" queue. Batch status → `UNDER_REVIEW`.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/validate`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Response Body

```json
{
  "success": true,
  "code": 200,
  "message": "Attendance validated and employees flagged",
  "data": {
    "id": "00000000-0000-0000-0000-000000000300",
    "batchStatus": "UNDER_REVIEW",
    "totalHeadcount": 25,
    "processedCount": 23,
    "flaggedCount": 2
  }
}
```

---

### 3.5 List Flagged Employees

Returns the review queue for a payroll batch — all employees flagged during attendance validation.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/batches/{id}/flagged`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Response Body

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000400",
      "payrollBatchId": "00000000-0000-0000-0000-000000000300",
      "employeeId": "00000000-0000-0000-0000-000000000003",
      "employeeName": "John Doe",
      "department": "Engineering",
      "unpaidLeaveDays": 3.0,
      "unauthorizedAbsenceDays": 0.0,
      "flagReason": "Unpaid leave: 3.0 days",
      "baseSalary": 150000.0000,
      "calculatedSalary": null,
      "totalWorkingDays": 22,
      "daysWorked": 19.0,
      "flagAction": "PENDING_REVIEW"
    }
  ],
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

| Flag Reason Field | Description |
|-------------------|-------------|
| `unpaidLeaveDays` | Approved unpaid leave days in the pay period |
| `unauthorizedAbsenceDays` | Days without any leave request |
| `totalWorkingDays` | Total business days in the period (excludes weekends) |
| `daysWorked` | Working days minus unpaid/unauthorized days |

---

### 3.6 Process Flagged Employee

Executive action on a flagged employee (`WAIVE` or `PRORATE`). Emits `FLAG_WAIVED` or `FLAG_PRORATED` audit log entry.

- **Method:** `PATCH`
- **Path:** `/api/v1/payroll/batches/{batchId}/flagged/{flaggedId}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

**Waive Action** (full salary processed):
```json
{
  "flagAction": "WAIVED",
  "actionById": "00000000-0000-0000-0000-000000000005",
  "actionReason": "Medical emergency — approved by HR"
}
```

**Prorate Action** (adjusted salary):
```json
{
  "flagAction": "PRORATED",
  "actionById": "00000000-0000-0000-0000-000000000005",
  "actionReason": "Unauthorized absence confirmed"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `flagAction` | enum | ✅ | `WAIVED`, `PRORATED` | Executive decision |
| `actionById` | UUID | ✅ | Must be an Employee (executive) | Who took the action |
| `actionReason` | string | ✅ (for WAIVED) | max 500 chars | Justification for decision |

#### Proration Formula

```
calculatedSalary = baseSalary × (daysWorked ÷ totalWorkingDays)
```

All subsequent allowances, deductions, taxes, and social security are recalculated based on the `calculatedSalary`.

---

### 3.7 Calculate Salaries

Calculates gross salary, applies tax deductions via jurisdiction-specific [`TaxCalculationStrategy`](#5-tax-configuration), computes net salary, and creates [`Payslip`](#4-payslips) records with [`PayslipLineItem`](#) children for all non-flagged employees. Updates batch financial summary totals.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/calculate`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

> **Prerequisite:** All flagged employees must be [processed](#36-process-flagged-employee) (not in `PENDING_REVIEW` state) before calculation.

---

### 3.8 Generate Payslips

Generates payslip PDFs for all employees in the batch. PDFs are encrypted and stored in MinIO. For batches ≤50 employees, generation is synchronous. For >50 employees, generation is asynchronous via `PAYSLIP_GENERATED` outbox event.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/generate-payslips`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

---

### 3.9 Approve Payroll

Finance Manager approves the payroll batch. The system:
1. Validates batch is in `UNDER_REVIEW` state
2. Sets status → `APPROVED`
3. Generates unique `journalId`
4. Creates **payroll ledger entries** (double-entry, PAY-03)
5. Emits `PAYROLL_APPROVED` outbox event (triggers downstream ledger sync)

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/approve`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "approvedById": "00000000-0000-0000-0000-000000000005"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `approvedById` | UUID | ❌ | Must be Employee (Finance Manager) | Who approved the payroll |

#### Ledger Entries Created (PAY-03.2)

| Entry Side | Account Code | Description | Amount |
|------------|-------------|-------------|--------|
| DEBIT | `SALARY_EXPENSE` | Salary Expense | SUM(all gross salaries) |
| CREDIT | `EMPLOYEE_PAYABLE` | Employee Payable | SUM(all net salaries) |
| CREDIT | `TAX_WITHHOLDING_PAYABLE` | Tax Withholding Payable | SUM(all tax deductions) |

**Validation:** `SUM(DEBIT) == SUM(CREDIT)` before persisting.

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | `PAY_PB_001` | Payroll batch not found |
| 422 | `PAY_PB_003` | Cannot transition to APPROVED from current state |

---

### 3.10 Reject Payroll

Finance Manager rejects the payroll batch, returning it to HR for corrections. Emits `PAYROLL_REJECTED` outbox event.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/reject`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{
  "rejectionReason": "Incorrect tax slab applied. Please reconfigure."
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `rejectionReason` | string | ✅ | max 500 chars | Reason for rejection |

---

### 3.11 Void Payroll

Voids an approved payroll batch. Creates **reversing ledger entries** (debits ↔ credits) with `isReversal=true`, linked to original entries via `originalEntryId`. Batch status → `VOIDED`. Emits `PAYROLL_VOIDED` outbox event.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/batches/{id}/void`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body

```json
{}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| *(none)* | — | — | — | No body required for void |

#### Errors

| Status | Code | Message |
|--------|------|---------|
| 404 | `PAY_PB_001` | Payroll batch not found |
| 422 | `PAY_PB_003` | Can only void APPROVED or APPROVED_PENDING_PAYMENT batches |

---

### 3.12 Get Ledger Entries

Returns all payroll ledger entries for a batch, grouped by journal ID.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/batches/{id}/ledger`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Response Body

```json
{
  "success": true,
  "code": 200,
  "message": "Records retrieved successfully",
  "data": [
    {
      "id": "00000000-0000-0000-0000-000000000500",
      "journalId": "00000000-0000-0000-0000-000000000600",
      "payrollBatchId": "00000000-0000-0000-0000-000000000300",
      "accountCode": "SALARY_EXPENSE",
      "accountDescription": "Salary Expense",
      "entrySide": "DEBIT",
      "amount": 3750000.0000,
      "currencyCode": "NPR",
      "amountUsd": null,
      "isReversal": false,
      "referenceType": "PAYROLL",
      "referenceId": "00000000-0000-0000-0000-000000000300"
    },
    {
      "accountCode": "EMPLOYEE_PAYABLE",
      "entrySide": "CREDIT",
      "amount": 2800000.0000
    },
    {
      "accountCode": "TAX_WITHHOLDING_PAYABLE",
      "entrySide": "CREDIT",
      "amount": 950000.0000
    }
  ],
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

---

## 4. Payslips

**Base path:** `/api/v1/payroll/payslips`

> **Prerequisite:** A [payroll batch](#31-initiate-payroll-batch) must have been [calculated](#37-calculate-salaries) and [payslips generated](#38-generate-payslips).

### 4.1 List My Payslips

Returns all payslips for a given employee, ordered by pay period start descending (employee self-service).

- **Method:** `GET`
- **Path:** `/api/v1/payroll/payslips`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `employeeId` | UUID | ✅ | — | Employee whose payslips to list |

---

### 4.2 Get Payslip Detail

Returns full payslip detail including all [`PayslipLineItem`](#) entries (earnings, deductions, employer expenses).

- **Method:** `GET`
- **Path:** `/api/v1/payroll/payslips/{id}`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`

#### Response Body

```json
{
  "success": true,
  "code": 200,
  "message": "Record retrieved successfully",
  "data": {
    "id": "00000000-0000-0000-0000-000000000700",
    "payrollBatchId": "00000000-0000-0000-0000-000000000300",
    "employeeId": "00000000-0000-0000-0000-000000000003",
    "employeeName": "John Doe",
    "employeeCode": "EMP-001",
    "department": "Engineering",
    "payPeriodStart": "2026-05-01",
    "payPeriodEnd": "2026-05-31",
    "paymentDate": "2026-05-30",
    "totalWorkingDays": 22,
    "daysWorked": 22.0,
    "paidLeaveDays": 0.0,
    "sickLeaveDays": 0.0,
    "unpaidLeaveDays": 0.0,
    "grossSalary": 150000.0000,
    "totalDeductions": 46500.0000,
    "netSalary": 103500.0000,
    "ytdGrossEarnings": 750000.0000,
    "ytdTaxes": 232500.0000,
    "currencyCode": "NPR",
    "payslipPdfPath": "/payslips/00000000-0000-0000-0000-000000000700.pdf",
    "isDownloaded": false,
    "lineItems": [
      {
        "id": "00000000-0000-0000-0000-000000000710",
        "lineItemType": "EARNING",
        "lineItemCode": "BASE_SALARY",
        "lineItemDescription": "Base Salary",
        "amount": 150000.0000,
        "currencyCode": "NPR",
        "displayOrder": 1
      },
      {
        "lineItemType": "DEDUCTION",
        "lineItemCode": "NP_SSF_EMPLOYEE",
        "lineItemDescription": "Social Security Fund - Employee Contribution (11%)",
        "amount": 16500.0000,
        "displayOrder": 100
      },
      {
        "lineItemType": "EMPLOYER_EXPENSE",
        "lineItemCode": "NP_SSF_EMPLOYER",
        "lineItemDescription": "Social Security Fund - Employer Contribution (20%)",
        "amount": 30000.0000,
        "displayOrder": 101
      },
      {
        "lineItemType": "DEDUCTION",
        "lineItemCode": "NP_INCOME_TAX",
        "lineItemDescription": "Income Tax (IRD Progressive)",
        "amount": 30000.0000,
        "displayOrder": 102
      }
    ],
    "createdAt": "2026-06-01T10:00:00"
  },
  "timestamp": "2026-06-01T10:00:00.000Z"
}
```

| Line Item Type | Display | Affects Net Salary |
|----------------|---------|--------------------|
| `EARNING` | Shown as earnings | **Yes** (adds to gross) |
| `DEDUCTION` | Shown as deductions | **Yes** (subtracts from net) |
| `EMPLOYER_EXPENSE` | Shown for accounting | **No** (not deducted from employee) |

#### Line Item Codes by Jurisdiction

| Code | Jurisdiction | Type | Description |
|------|-------------|------|-------------|
| `BASE_SALARY` | All | EARNING | Base monthly salary |
| `NP_SSF_EMPLOYEE` | Nepal | DEDUCTION | SSF employee contribution (11%) |
| `NP_SSF_EMPLOYER` | Nepal | EMPLOYER_EXPENSE | SSF employer contribution (20%) |
| `NP_INCOME_TAX` | Nepal | DEDUCTION | IRD progressive income tax |
| `IN_PF_EMPLOYEE` | India | DEDUCTION | PF employee contribution (12%) |
| `IN_PF_EMPLOYER` | India | EMPLOYER_EXPENSE | PF employer contribution (12%) |
| `IN_PROFESSIONAL_TAX` | India | DEDUCTION | State professional tax (fixed) |
| `IN_INCOME_TAX` | India | DEDUCTION | Income tax (progressive) |

---

### 4.3 Download Payslip PDF

Downloads the encrypted payslip PDF. Returns raw PDF bytes with `Content-Type: application/pdf`.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/payslips/{id}/pdf`
- **Auth:** `isAuthenticated()`
- **Status:** `200 OK`
- **Response:** `binary (application/pdf)`

---

### 4.4 List Payslips by Batch

Returns all payslips for a given payroll batch (admin/HR view).

- **Method:** `GET`
- **Path:** `/api/v1/payroll/payslips/batch/{batchId}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

---

## 5. Tax Configuration

**Base path:** `/api/v1/payroll/tax-configurations`

> **Prerequisite:** A [`LegalEntity`](docs/api-endpoints-finance.md#11-create-entity) must exist in `APPROVED` state before configuring tax rules.

Tax configurations are **per-entity, per-jurisdiction**. Each entity can have one active `TaxConfiguration` per [`Jurisdiction`](src/main/java/com/af/novadesk/api/payroll/constants/Jurisdiction.java:1).

### 5.1 List Tax Configurations by Entity

Returns all tax configurations for a legal entity.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/tax-configurations`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Query Parameters

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `legalEntityId` | UUID | ✅ | — | Entity to list configs for |

---

### 5.2 Get Tax Configuration

Returns a single tax configuration by ID.

- **Method:** `GET`
- **Path:** `/api/v1/payroll/tax-configurations/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

---

### 5.3 Create Tax Configuration

Creates a new tax configuration for an entity. Includes tax slabs for progressive income tax calculation. Emits `TAX_CONFIG_UPDATED` audit log entry.

- **Method:** `POST`
- **Path:** `/api/v1/payroll/tax-configurations`
- **Auth:** `organizations:write`
- **Status:** `201 Created`

#### Request Body (Nepal Example)

```json
{
  "legalEntityId": "00000000-0000-0000-0000-000000000010",
  "jurisdiction": "NEPAL",
  "ssfEmployeeRate": 0.11,
  "ssfEmployerRate": 0.20,
  "ssfMaxCapAmount": 50000.0000,
  "effectiveFrom": "2025-07-16",
  "taxSlabs": [
    {
      "slabOrder": 1,
      "incomeFrom": 0.0000,
      "incomeTo": 500000.0000,
      "taxRate": 0.01,
      "isAnnual": true,
      "description": "NPR 0 - 500,000: 1%"
    },
    {
      "slabOrder": 2,
      "incomeFrom": 500000.0000,
      "incomeTo": 700000.0000,
      "taxRate": 0.10,
      "isAnnual": true,
      "description": "NPR 500,001 - 700,000: 10%"
    },
    {
      "slabOrder": 3,
      "incomeFrom": 700000.0000,
      "incomeTo": 1000000.0000,
      "taxRate": 0.20,
      "isAnnual": true,
      "description": "NPR 700,001 - 1,000,000: 20%"
    },
    {
      "slabOrder": 4,
      "incomeFrom": 1000000.0000,
      "incomeTo": 2000000.0000,
      "taxRate": 0.30,
      "isAnnual": true,
      "description": "NPR 1,000,001 - 2,000,000: 30%"
    },
    {
      "slabOrder": 5,
      "incomeFrom": 2000000.0000,
      "incomeTo": null,
      "taxRate": 0.36,
      "isAnnual": true,
      "description": "NPR 2,000,001+: 36%"
    }
  ]
}
```

#### Request Body (India Example)

```json
{
  "legalEntityId": "00000000-0000-0000-0000-000000000011",
  "jurisdiction": "INDIA",
  "pfEmployeeRate": 0.12,
  "pfEmployerRate": 0.12,
  "pfMaxCapAmount": 15000.0000,
  "professionalTaxAmount": 200.0000,
  "professionalTaxState": "Karnataka",
  "effectiveFrom": "2025-04-01",
  "taxSlabs": [
    {
      "slabOrder": 1,
      "incomeFrom": 0.0000,
      "incomeTo": 250000.0000,
      "taxRate": 0.00,
      "description": "INR 0 - 250,000: 0%"
    },
    {
      "slabOrder": 2,
      "incomeFrom": 250000.0000,
      "incomeTo": 500000.0000,
      "taxRate": 0.05,
      "description": "INR 250,001 - 500,000: 5%"
    },
    {
      "slabOrder": 3,
      "incomeFrom": 500000.0000,
      "incomeTo": 1000000.0000,
      "taxRate": 0.20,
      "description": "INR 500,001 - 1,000,000: 20%"
    },
    {
      "slabOrder": 4,
      "incomeFrom": 1000000.0000,
      "incomeTo": null,
      "taxRate": 0.30,
      "description": "INR 1,000,001+: 30%"
    }
  ]
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `legalEntityId` | UUID | ✅ | Entity must be `APPROVED` | Entity for tax configuration |
| `jurisdiction` | enum | ✅ | `NEPAL`, `INDIA`, `USA` | Tax jurisdiction |
| `ssfEmployeeRate` | number | ❌ (Nepal only) | 0–1 (e.g., 0.11 = 11%) | SSF employee contribution rate |
| `ssfEmployerRate` | number | ❌ (Nepal only) | 0–1 | SSF employer contribution rate |
| `ssfMaxCapAmount` | number | ❌ (Nepal only) | > 0 | Max gross salary for SSF calculation (NPR 50,000) |
| `pfEmployeeRate` | number | ❌ (India only) | 0–1 | PF employee contribution rate |
| `pfEmployerRate` | number | ❌ (India only) | 0–1 | PF employer contribution rate |
| `pfMaxCapAmount` | number | ❌ (India only) | > 0 | Max Basic+DA for PF calculation (INR 15,000) |
| `professionalTaxAmount` | number | ❌ (India only) | ≥ 0 | Fixed monthly professional tax |
| `professionalTaxState` | string | ❌ (India only) | max 50 chars | State name (e.g., `Karnataka`) |
| `effectiveFrom` | date | ✅ | Past or present | Date tax rules take effect |
| `effectiveTo` | date | ❌ | After `effectiveFrom` | Null = currently active |
| `isActive` | boolean | ❌ | Default: `true` | Whether this configuration is active |

#### TaxSlab Fields

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `slabOrder` | int | ✅ | > 0, unique per config | Order of slab application |
| `incomeFrom` | number | ✅ | ≥ 0 | Lower bound (inclusive), annual |
| `incomeTo` | number | ❌ | > `incomeFrom`; `null` = unlimited | Upper bound (exclusive) |
| `taxRate` | number | ✅ | 0–1 (e.g., 0.01 = 1%) | Tax rate for this slab |
| `isAnnual` | boolean | ❌ | Default: `true` | `true` = annualized (divide by 12 for monthly) |
| `description` | string | ❌ | max 200 chars | Human-readable slab description |

#### Jurisdiction-Specific Rate Defaults

| Jurisdiction | SSF/PF Employee | SSF/PF Employer | Max Cap | Professional Tax | Tax Slabs |
|-------------|-----------------|-----------------|---------|------------------|-----------|
| `NEPAL` | 11% (SSF) | 20% (SSF) | NPR 50,000 | N/A | IRD progressive (1%–36%) |
| `INDIA` | 12% (PF) | 12% (PF) | INR 15,000 | INR 200/month | Income Tax progressive (0%–30%) |
| `USA` | Future | Future | Future | Future | Future |

---

### 5.4 Update Tax Configuration

Partially updates an existing tax configuration. Only provided non-null fields are updated. Emits `TAX_CONFIG_UPDATED` audit log entry. Changes apply to the next payroll cycle.

- **Method:** `PUT`
- **Path:** `/api/v1/payroll/tax-configurations/{id}`
- **Auth:** `organizations:write`
- **Status:** `200 OK`

#### Request Body (partial update example)

```json
{
  "ssfEmployeeRate": 0.12,
  "isActive": false,
  "effectiveTo": "2026-06-30"
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| *(any field from create)* | — | ❌ | Same constraints as create | Only non-null fields are updated |
| `isActive` | boolean | ❌ | — | Deactivate this configuration |
| `effectiveTo` | date | ❌ | — | End date for this configuration |

---

## 6. Endpoint Dependency Map

The following diagram shows the required order of operations:

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Finance Module Prerequisites                                 │
│    ├── Create LegalEntity (APPROVED)                            │
│    ├── Approve Entity → creates FiscalYearSetting               │
│    └── (Optional) Configure Exchange Rates                      │
├─────────────────────────────────────────────────────────────────┤
│ 2. Employee Setup                                               │
│    ├── POST /payroll/employees        (onboard employee)        │
│    │   └── Auto-creates LeaveBalance (Paid/Sick/Unpaid)        │
│    └── (Repeat for all employees in entity)                     │
├─────────────────────────────────────────────────────────────────┤
│ 3. Tax Configuration                                            │
│    └── POST /payroll/tax-configurations (per jurisdiction)      │
├─────────────────────────────────────────────────────────────────┤
│ 4. Leave Management (ongoing)                                   │
│    ├── POST /payroll/leaves/requests  (submit)                  │
│    ├── POST /payroll/leaves/requests/{id}/approve               │
│    └── ...                                                      │
├─────────────────────────────────────────────────────────────────┤
│ 5. Payroll Run (monthly)                                        │
│    ├── POST /payroll/batches           (initiate)               │
│    ├── POST /payroll/batches/{id}/validate (attendance)        │
│    ├── PATCH /payroll/batches/{id}/flagged/{fid} (process)     │
│    ├── POST /payroll/batches/{id}/calculate (salaries)         │
│    ├── POST /payroll/batches/{id}/generate-payslips (PDFs)    │
│    └── POST /payroll/batches/{id}/approve  (finalize)          │
├─────────────────────────────────────────────────────────────────┤
│ 6. Employee Self-Service                                        │
│    ├── GET /payroll/payslips?employeeId=...   (list)            │
│    ├── GET /payroll/payslips/{id}              (detail)         │
│    └── GET /payroll/payslips/{id}/pdf          (download)       │
└─────────────────────────────────────────────────────────────────┘
```

### Workflow State Dependencies

| Endpoint | Must Be Completed First |
|----------|------------------------|
| `POST /payroll/employees` | `POST /legal-entities` + `POST /legal-entities/{id}/approve` |
| `POST /payroll/leaves/requests` | `POST /payroll/employees` (LeaveBalance auto-created) |
| `POST /payroll/leaves/requests/{id}/approve` | `POST /payroll/leaves/requests` (must be PENDING) |
| `POST /payroll/leaves/requests/{id}/cancel` | Leave request — ≥2 days before start |
| `POST /payroll/batches` | `POST /payroll/employees` + `POST /payroll/tax-configurations` |
| `POST /payroll/batches/{id}/validate` | `POST /payroll/batches` (must be INITIATED) |
| `PATCH /payroll/batches/{id}/flagged/{fid}` | `POST /payroll/batches/{id}/validate` |
| `POST /payroll/batches/{id}/calculate` | All flagged employees processed |
| `POST /payroll/batches/{id}/generate-payslips` | `POST /payroll/batches/{id}/calculate` |
| `POST /payroll/batches/{id}/approve` | `POST /payroll/batches/{id}/generate-payslips` (must be UNDER_REVIEW) |
| `POST /payroll/batches/{id}/void` | Batch must be APPROVED or APPROVED_PENDING_PAYMENT |
| `GET /payroll/payslips` | Payslips generated |

---

## 7. Common Error Response Shapes

All errors follow a consistent shape modeled after the `FinanceExceptionHandler`.

### Validation Error (400)

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": {
    "startDate": "Start date cannot be in the past",
    "numberOfDays": "Number of days must be positive"
  },
  "path": "/api/v1/payroll/leaves/requests",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Not Found (404)

```json
{
  "success": false,
  "errorCode": "PAY_EMP_001",
  "message": "Employee not found: 00000000-0000-0000-0000-000000000999",
  "details": null,
  "path": "/api/v1/payroll/employees/00000000-0000-0000-0000-000000000999",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Conflict (409)

```json
{
  "success": false,
  "errorCode": "PAY_PB_002",
  "message": "Payroll batch already exists for entity 00000000-0000-0000-0000-000000000010, period 2026-05-01 to 2026-05-31",
  "details": null,
  "path": "/api/v1/payroll/batches",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Unprocessable Entity / State Error (422)

```json
{
  "success": false,
  "errorCode": "PAY_PB_003",
  "message": "Cannot transition payroll batch 00000000-0000-0000-0000-000000000300 from APPROVED to UNDER_REVIEW",
  "details": null,
  "path": "/api/v1/payroll/batches/00000000-0000-0000-0000-000000000300/validate",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Payroll Pre-Check Failure (422)

```json
{
  "success": false,
  "errorCode": "PAY_PB_005",
  "message": "Payroll pre-checks failed for entity 00000000-0000-0000-0000-000000000010: 2 issues found",
  "details": [
    "3 employees have pending leave requests for this period",
    "Tax configuration not found for jurisdiction: NEPAL"
  ],
  "path": "/api/v1/payroll/batches",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Internal Server Error (500)

```json
{
  "success": false,
  "errorCode": "INTERNAL_ERROR",
  "message": "An unexpected error occurred. Please try again.",
  "details": null,
  "path": "/api/v1/payroll/batches/00000000-0000-0000-0000-000000000300/calculate",
  "timestamp": "2026-06-01T10:00:00.000"
}
```

### Payroll Error Code Reference

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `PAY_EMP_001` | 404 | Employee not found |
| `PAY_EMP_002` | 409 | Duplicate employee (same ShadowUser + Entity) |
| `PAY_LR_001` | 404 | Leave request not found |
| `PAY_LR_002` | 422 | Invalid leave state transition |
| `PAY_LR_003` | 400 | Invalid leave date |
| `PAY_LB_001` | 404 | Leave balance not found |
| `PAY_LB_002` | 422 | Insufficient leave balance |
| `PAY_PB_001` | 404 | Payroll batch not found |
| `PAY_PB_002` | 409 | Payroll batch already exists for period |
| `PAY_PB_003` | 422 | Invalid payroll state transition |
| `PAY_PB_004` | 500 | Payroll processing error |
| `PAY_PB_005` | 422 | Payroll pre-check failure |
| `PAY_PS_001` | 404 | Payslip not found |
| `PAY_PS_002` | 500 | Payslip PDF generation failed |
| `PAY_PFE_001` | 404 | Flagged employee record not found |
| `PAY_TC_001` | 404 | Tax configuration not found |
| `PAY_TC_002` | 400 | Invalid tax configuration |
| `PAY_PLE_001` | 400 | Unbalanced payroll ledger entries |
| `VALIDATION_ERROR` | 400 | Request body validation failed |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
