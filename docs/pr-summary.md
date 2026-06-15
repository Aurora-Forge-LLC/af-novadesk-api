# PR Summary: Payroll Module — Complete Implementation

## Overview

This PR delivers the **complete Payroll Module** (`com.af.novadesk.api.payroll`) as a new bounded context within the NovaDesk modular monolith, alongside cross-module integration with AuthHub (identity) and Finance modules and refactoring of shared infrastructure config.

**Module scope:** LLR-PAY-01 (Leave Management), LLR-PAY-02 (Payroll Processing), PAY-03 (Payroll Ledger Entries)

**Total new files:** ~95  
**Total modified files:** ~25 (cross-module refactoring, identity module changes, test fixes)

---

## Module Architecture

```
payroll/
├── api/              (5  files) — OpenAPI-annotated interfaces with @PreAuthorize
├── config/           (3  files) — SecurityConfig, ModuleConfig, AuthHubProperties
├── constants/        (10 files) — Enums for states, types, events, jurisdictions
├── controller/       (5  files) — REST controllers implementing API interfaces
├── dto/              (13 files) — Request/response DTOs with Jakarta validation
├── entity/           (15 files) — JPA entities with @Filter org-scoping
├── exception/        (21 files) — Domain-specific exceptions + global handler
├── mapper/           (3  files) — Entity ↔ DTO mappers
├── repository/       (15 files) — Spring Data JPA repositories
├── scheduler/        (1  file)  — LeaveBalanceRolloverJob (cron)
└── service/
    ├── service/      (11 files) — Service interfaces
    └── impl/         (10 files) — Service implementations
```

---

## Key Changes

### 1. Cross-Module Infrastructure Refactoring

| File | Change |
|------|--------|
| [`AbstractEntity.java`](src/main/java/com/af/novadesk/api/common/entity/AbstractEntity.java) | Moved from `finance.entity` to `common.entity` for shared use across all modules. Updated 22+ import statements across finance, payroll, identity. |
| [`CommonModuleConfig.java`](src/main/java/com/af/novadesk/api/common/config/CommonModuleConfig.java) | Added `@EnableJpaAuditing`, `@EnableMethodSecurity`, `@EnableTransactionManagement` (moved from individual module configs to prevent bean-definition conflicts). Added `commonObjectMapper(Jackson2ObjectMapperBuilder)` for shared JSON serialization. |
| [`MasterSecurityConfig.java`](src/main/java/com/af/novadesk/api/common/security/MasterSecurityConfig.java) | Added CORS configuration source for infrastructure endpoints. Catch-all filter chain at `@Order(Integer.MAX_VALUE)`. |
| [`PayrollSecurityConfig.java`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java) | Module-specific security at `@Order(2)`. JWT authentication via OAuth2 Resource Server. Custom `JwtAuthenticationConverter` mapping `permissions` claim to Spring Security authorities. Custom audience validator. CORS + stateless sessions. |
| [`IdentitySecurityContext.java`](src/main/java/com/af/novadesk/api/identity/security/IdentitySecurityContext.java) | Typed accessor extracting `sub` (authUserId), `organizationId`, `email`, `name` from JWT. Also exposes `getTokenValue()` for service-to-service AuthHub calls. |
| [`IdentityJwtAuthenticationFilter.java`](src/main/java/com/af/novadesk/api/identity/security/IdentityJwtAuthenticationFilter.java) | Runs after JWT validation; auto-upserts `ShadowUser` records for every authenticated request. |
| Tests: [`CommonModuleConfigTest`](src/test/java/com/af/novadesk/api/common/config/CommonModuleConfigTest.java), [`MasterSecurityConfigTest`](src/test/java/com/af/novadesk/api/common/security/MasterSecurityConfigTest.java) | Fixed parameter mismatch (`commonObjectMapper` now requires `Jackson2ObjectMapperBuilder`) and missing `.cors()` mock stub. |

### 2. AuthHub Integration

| File | Purpose |
|------|---------|
| [`AuthHubProperties.java`](src/main/java/com/af/novadesk/api/payroll/config/AuthHubProperties.java) | Config properties for af-authhub base URL |
| [`AuthHubClientService.java`](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java) | HTTP client calling `POST /api/v1/admin/users` on af-authhub during reversed employee onboarding. Forwards current JWT token. Requires `organizations:write` in caller's permissions. |
| [`AuthHubIntegrationException.java`](src/main/java/com/af/novadesk/api/payroll/exception/AuthHubIntegrationException.java) | Exception for AuthHub call failures (triggers `@Transactional` rollback on ShadowUser creation) |
| [`PayrollModuleConfig.java`](src/main/java/com/af/novadesk/api/payroll/config/PayrollModuleConfig.java) | Registers `RestTemplate` bean with 5s/10s connect/read timeouts for AuthHub calls |

### 3. Employee Management (LLR-PAY-01.1)

**Entity:** [`Employee`](src/main/java/com/af/novadesk/api/payroll/entity/Employee.java) — Payroll role extending `ShadowUser` via FK. Self-referential `manager_id` for approval hierarchy. Denormalized `organizationId` and `authUserId` for query performance.

**API endpoints** (`/api/v1/payroll/employees`, all require `organizations:write`):
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/employees` | Onboard employee — supports two flows: legacy (existing `shadowUserId`) and reversed (auto-creates ShadowUser + provisions user in AuthHub with EMPLOYEE role) |
| `GET` | `/employees` | List employees (optional `legalEntityId` filter) |
| `GET` | `/employees/{id}` | Get employee by ID |
| `PATCH` | `/employees/{id}` | Update employee (partial) |
| `POST` | `/employees/{id}/terminate` | Terminate employee (sets `terminationDate`, marks INACTIVE) |

On onboarding, auto-creates 3 `LeaveBalance` records (Paid=10, Sick=5, Unpaid=999) linked to the current fiscal year.

### 4. Leave Management (LLR-PAY-01.2–01.5)

**Entities:** `LeaveRequest`, `LeaveBalance`, `LeaveTransaction`

**API endpoints** (`/api/v1/payroll/leaves`, all `isAuthenticated()`):
| Method | Path | Description | State Transition |
|--------|------|-------------|------------------|
| `POST` | `/leaves/requests` | Submit leave request with balance check & auto-conversion | → `PENDING` |
| `GET` | `/leaves/requests/{id}` | Get leave request detail | — |
| `GET` | `/leaves/requests` | List by employee or organization | — |
| `GET` | `/leaves/pending` | Pending requests for approver dashboard | — |
| `POST` | `/leaves/requests/{id}/approve` | Approve (deducts balances, creates transactions) | → `APPROVED` |
| `POST` | `/leaves/requests/{id}/reject` | Reject (restores pending days) | → `REJECTED` |
| `POST` | `/leaves/requests/{id}/modify` | Request modification (manager suggests alternate dates) | → `MODIFICATION_REQUESTED` |
| `POST` | `/leaves/requests/{id}/cancel` | Cancel (≥2 days before start; restores balances) | → `CANCELLED` |
| `GET` | `/leaves/balances` | Get leave balances for employee | — |

**Key behaviors:**
- Balance auto-conversion: excess Paid/Sick days automatically become Unpaid
- Manager hierarchy: `employee.manager_id` → set as `approver` on submission
- Immutable audit trail: `LeaveTransaction` records (DEDUCTION, RESTORATION, ALLOCATION)
- Outbox events: `LEAVE_REQUESTED`, `LEAVE_APPROVED`, `LEAVE_REJECTED`, `LEAVE_MODIFICATION_REQUESTED`, `LEAVE_CANCELLED`

### 5. Payroll Batch Processing (LLR-PAY-02)

**Entity:** `PayrollBatch` with state machine: `INITIATED` → `UNDER_REVIEW` → `APPROVED` / `REJECTED` / `VOIDED`

**Supporting entities:** `PayrollFlaggedEmployee`, `PayrollLedgerEntry`

**API endpoints** (`/api/v1/payroll/batches`, all require `organizations:write`):
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/batches` | Initiate payroll batch for entity + pay period |
| `GET` | `/batches` | List all batches (optional `legalEntityId` filter) |
| `GET` | `/batches/{id}` | Get batch detail |
| `POST` | `/batches/{id}/validate` | Validate attendance, flag employees with unpaid leave/absences |
| `GET` | `/batches/{id}/flagged` | List flagged employees (review queue) |
| `PATCH` | `/batches/{batchId}/flagged/{flaggedId}` | Process flagged employee (`WAIVED` or `PRORATED`) |
| `POST` | `/batches/{id}/calculate` | Calculate salaries, apply tax deductions, create payslips with line items |
| `POST` | `/batches/{id}/generate-payslips` | Generate payslip PDFs (synchronous ≤50 employees, async >50) |
| `POST` | `/batches/{id}/approve` | Approve payroll, create double-entry ledger entries (SALARY_EXPENSE / EMPLOYEE_PAYABLE / TAX_WITHHOLDING_PAYABLE) |
| `POST` | `/batches/{id}/reject` | Reject payroll with reason |
| `POST` | `/batches/{id}/void` | Void approved payroll, create reversing ledger entries |
| `GET` | `/batches/{id}/ledger` | Get payroll ledger entries |

**Proration formula:** `calculatedSalary = baseSalary × (daysWorked / totalWorkingDays)`

**Ledger entries (PAY-03.2):** DEBIT `SALARY_EXPENSE`, CREDIT `EMPLOYEE_PAYABLE`, CREDIT `TAX_WITHHOLDING_PAYABLE`

### 6. Tax Engine

**Entities:** `TaxConfiguration` (per-entity, per-jurisdiction), `TaxSlab` (progressive rate bands)

**Strategy pattern:** `TaxCalculationStrategy` interface with implementations:
- [`NepalTaxStrategy`](src/main/java/com/af/novadesk/api/payroll/service/impl/NepalTaxStrategy.java): IRD progressive tax (1%–36%), SSF employee (11%), SSF employer (20%), NPR 50,000 max cap
- [`IndiaTaxStrategy`](src/main/java/com/af/novadesk/api/payroll/service/impl/IndiaTaxStrategy.java): Income tax (0%–30%), PF employee (12%), PF employer (12%), INR 15,000 max cap, professional tax
- USA: Future

**TaxConfiguration API** (`/api/v1/payroll/tax-configurations`, require `organizations:write`): GET (list/by-entity), GET by ID, POST (create with slabs), PUT (update).

### 7. Payslips

**Entities:** `Payslip`, `PayslipLineItem` (EARNING / DEDUCTION / EMPLOYER_EXPENSE)

**Payslip API** (`/api/v1/payroll/payslips`):
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/payslips` | `isAuthenticated()` | List employee's payslips |
| `GET` | `/payslips/{id}` | `isAuthenticated()` | Payslip detail with line items |
| `GET` | `/payslips/{id}/pdf` | `isAuthenticated()` | Download payslip PDF |
| `GET` | `/payslips/batch/{batchId}` | `organizations:write` | List payslips by batch (admin view) |

### 8. Outbox Event System

Three outbox aggregates producing events for downstream services:

| Aggregate | Outbox Table | Events |
|-----------|-------------|--------|
| `LeaveRequest` | `leave_request_outbox_events` | `LEAVE_REQUESTED`, `LEAVE_APPROVED`, `LEAVE_REJECTED`, `LEAVE_MODIFICATION_REQUESTED`, `LEAVE_CANCELLED` |
| `PayrollBatch` | `payroll_batch_outbox_events` | `PAYROLL_INITIATED`, `PAYROLL_APPROVED`, `PAYROLL_REJECTED`, `PAYROLL_VOIDED` |
| `Payslip` | `payslip_outbox_events` | `PAYSLIP_GENERATED` |

### 9. Exception Handling

[`PayrollExceptionHandler`](src/main/java/com/af/novadesk/api/payroll/exception/PayrollExceptionHandler.java) — `@RestControllerAdvice` handling 21 custom exception types with consistent `ApiResponse` error shapes. Error codes: `PAY_EMP_001–002`, `PAY_LR_001–003`, `PAY_LB_001–002`, `PAY_PB_001–005`, `PAY_PS_001–002`, `PAY_PFE_001`, `PAY_TC_001–002`, `PAY_PLE_001`.

### 10. Scheduled Job

[`LeaveBalanceRolloverJob`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveBalanceRolloverJob.java) — Runs daily at 2 AM. Detects fiscal year transitions per entity and creates new `LeaveBalance` records for all active employees. Old balances remain immutable for audit trails.

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| **Employee references ShadowUser via FK** | All employees must first exist as ShadowUsers (AuthHub identities). No class inheritance. |
| **Reversed onboarding flow** | NovaDesk creates ShadowUser first, then calls AuthHub to provision user. AuthHub call failure triggers `@Transactional` rollback. |
| **LeaveBalance aligned to FiscalYearSetting** | Leave reset aligns with entity's fiscal year (e.g. Nepal: mid-July, India: April). Proactive rollover via scheduled job. |
| **Payroll-specific PayrollLedgerEntry** | Payroll owns its ledger. Not shared with finance module's `fa_ledger_entries`. `referenceType = "PAYROLL"` for future consolidation. |
| **Outbox per aggregate** | One outbox table per aggregate with `event_type` discriminator. Reduces table sprawl, simplifies polling, keeps idempotency clean. |
| **Leave endpoints use `isAuthenticated()` only** | All leave lifecycle endpoints (approve, reject, modify, cancel) currently verify only JWT authenticity — no approver-identity check. Future enhancement needed. |
| **Admin endpoints require `organizations:write`** | Employee CRUD, payroll batches, tax configurations, and payslip admin views all require this permission. Employees (with `profile:read`, `profile:write`, `organizations:read`) cannot access these. |
| **Jurisdiction-specific tax strategy** | Strategy pattern via `TaxCalculationStrategyFactory` for Nepal (IRD + SSF), India (PF + Income Tax), with USA planned. Configurable tax slabs via API. |

---

## Known Limitations / Future Work

1. **No "whoami" endpoint** — Currently logged-in user cannot look up their own Employee record by JWT. `getEmployeeByAuthUserAndEntity()` exists in service but is not exposed via API.
2. **No approver identity verification** — Approve/reject/cancel endpoints do not verify the caller is the assigned approver or requesting employee.
3. **Multi-level approval not wired** — Entity defines `secondApprover` (HR) and `executiveApprover` (Finance), but service only sets direct manager as `approver`.
4. **PDF generation placeholder** — Payslip PDF generation/download returns empty bytes; MinIO integration pending.
5. **Attendance validation simplified** — Flagging logic commented; production would query `LeaveRequestRepository` for approved unpaid leave.
6. **`getEmployeeByCode()` query broken** — Uses `findByAuthUserIdAndLegalEntityId(null, ...)` instead of an employee-code lookup.

---

## File Count Breakdown

| Category | Count |
|----------|-------|
| API interfaces | 5 |
| Controllers | 5 |
| DTOs | 13 |
| Entities | 15 |
| Repositories | 15 |
| Mappers | 3 |
| Service interfaces | 11 |
| Service implementations | 10 |
| Constants/Enums | 10 |
| Exceptions | 20 |
| Exception handler | 1 |
| Config classes | 3 |
| Scheduler | 1 |
| Identity integration (SecurityContext, Filter, ShadowUser) | 3 |
| AuthHub integration (ClientService, Properties, Exception) | 3 |
| Flyway migrations | 15 |
| Docs | 2 (api-endpoints-payroll.md, pr-summary.md) |
| Plans | 4 |
| Test files | 2 |
| **Module config (application.yml changes)** | 1 |
| **Total** | **~142** |
