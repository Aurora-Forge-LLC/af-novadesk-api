# PR Review: Uncommitted Changes — af-novadesk-api

> **Review Date:** 2026-06-05  
> **Scope:** All unversioned and uncommitted files across Payroll, Finance, Identity, and Infrastructure modules  
> **Reviewer:** Automated Code Review  

---

## Executive Summary

This PR introduces **5 distinct feature epics** spanning the Payroll and Finance modules:

| # | Epic | Files Changed | Risk | Priority |
|---|------|--------------|------|----------|
| 1 | **Manager Role Onboarding (Reversed Flow)** | 14 files | 🟡 Moderate | High |
| 2 | **Transactional Outbox Pattern (Employee Events)** | 6 files | 🟡 Moderate | High |
| 3 | **Payroll Security Configuration** | 1 file | 🔴 High | Critical |
| 4 | **Leave Auto-Expiry Scheduled Job** | 7 files | 🟢 Low | Medium |
| 5 | **Role-Based Access for Leave Pending** | 2 files | 🟢 Low | Medium |

---

## Detailed Review by Epic

---

### Epic 1: Manager Role Onboarding (Reversed Employee Onboarding)

#### Files Modified

| File | Status | Lines |
|------|--------|-------|
| [`plans/manager-role-onboarding-plan.md`](plans/manager-role-onboarding-plan.md) | New | 317 |
| [`plans/frontend-prompt-for-reversed-onboarding.md`](plans/frontend-prompt-for-reversed-onboarding.md) | New | 118 |
| [`src/main/resources/db/migration/V1.69__add_manager_uuid_to_pr_employees.sql`](src/main/resources/db/migration/V1.69__add_manager_uuid_to_pr_employees.sql) | New | 16 |
| [`src/main/java/com/af/novadesk/api/payroll/entity/Employee.java`](src/main/java/com/af/novadesk/api/payroll/entity/Employee.java:158) | Modified | +`managerUuid` field |
| [`src/main/java/com/af/novadesk/api/payroll/dto/EmployeeDto.java`](src/main/java/com/af/novadesk/api/payroll/dto/EmployeeDto.java:76) | Modified | +`isManager`, `managerUuid` |
| [`src/main/java/com/af/novadesk/api/payroll/mapper/EmployeeMapper.java`](src/main/java/com/af/novadesk/api/payroll/mapper/EmployeeMapper.java:59) | Modified | +`isManager`/`managerUuid` mapping |
| [`src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java`](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java) | **New** | 128 |
| [`src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java) | Modified | Reversed onboarding flow |
| [`src/main/java/com/af/novadesk/api/common/event/EmployeeOnboardedEvent.java`](src/main/java/com/af/novadesk/api/common/event/EmployeeOnboardedEvent.java) | **New** | 53 |
| [`src/main/java/com/af/novadesk/api/finance/service/impl/EntityAccessSyncService.java`](src/main/java/com/af/novadesk/api/finance/service/impl/EntityAccessSyncService.java) | **New** | 61 |
| [`src/main/java/com/af/novadesk/api/finance/dto/EntityUserAccessDto.java`](src/main/java/com/af/novadesk/api/finance/dto/EntityUserAccessDto.java:38) | Modified | +`MANAGER` in entityRole regex |
| [`src/main/java/com/af/novadesk/api/payroll/service/EmployeeService.java`](src/main/java/com/af/novadesk/api/payroll/service/EmployeeService.java:46) | Modified | +`getCurrentEmployee()` |

#### ✅ Strengths

1. **Clean modular-monolith design** — The cross-module integration between Payroll (employee onboarding) and Finance (EntityUserAccess grant) is handled via a shared [`EmployeeOnboardedEvent`](src/main/java/com/af/novadesk/api/common/event/EmployeeOnboardedEvent.java) POJO in the `common` package, with zero JPA/code imports between modules. This is excellent architecture.

2. **Idempotent AuthHub integration** — The [`AuthHubClientService.deriveUserId()`](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java:123) method deterministically generates a UUID from `email + organizationId`, and the `createUser()` method [handles 409 Conflict](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java:105) as success. This makes the reversed onboarding flow retry-safe.

3. **Backward compatibility** — The [`EmployeeServiceImpl.onboardEmployee()`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:84) preserves the legacy flow when `shadowUserId` is provided, preventing breaking changes for existing callers.

4. **Comprehensive design documentation** — The plan documents ([`manager-role-onboarding-plan.md`](plans/manager-role-onboarding-plan.md), [`frontend-prompt-for-reversed-onboarding.md`](plans/frontend-prompt-for-reversed-onboarding.md)) are detailed with flow diagrams, API contracts, and frontend guidance.

#### ⚠️ Issues & Recommendations

| # | Severity | File | Issue | Recommendation |
|---|----------|------|-------|---------------|
| **1.1** | 🔴 **Bug** | [`EmployeeServiceImpl.java:244`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:244) | `getEmployeeByCode()` calls `findByAuthUserIdAndLegalEntityId(null, legalEntityId)` instead of the correct repository method for employee code lookup. This will never find an employee by code since `authUserId` is null. | Change to use the correct repository query: `findByEmployeeCodeAndLegalEntityId(employeeCode, legalEntityId)` |
| **1.2** | 🟡 **Concern** | [`EmployeeServiceImpl.java:92`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:92) | `deriveUserId()` uses `email.trim().toLowerCase()` which may produce a different UUID if the email case changes between retries | Normalize email to lowercase before persisting the ShadowUser record on line 119 |
| **1.3** | 🟡 **Concern** | [`EmployeeServiceImpl.java:96-100`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:96-100) | Email uniqueness check is performed **before** the AuthHub call. If AuthHub succeeds but the DB email check passes due to a different case, the ShadowUser could be duplicated. | Apply `LOWER(email)` in the `findByEmail()` query or standardize email casing before the check |
| **1.4** | 🟢 **Nitpick** | [`EmployeeOnboardedEvent.java:51`](src/main/java/com/af/novadesk/api/common/event/EmployeeOnboardedEvent.java:51) | `idempotencyKey` is constructed in the constructor as `"EMPLOYEE_ONBOARDED:" + employeeId`. However, if `employeeId` is null during construction, the key becomes `"EMPLOYEE_ONBOARDED:null"` | Validate `employeeId` is not null or add a null-safe builder |
| **1.5** | 🟢 **Nitpick** | [`EmployeeServiceImpl.java:162`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:162) | `entityRole = isManager ? "MANAGER" : "VIEWER"` — hardcoded string literals. If the [`EntityUserAccessDto`](src/main/java/com/af/novadesk/api/finance/dto/EntityUserAccessDto.java:37) regex pattern changes, this will silently break. | Extract role constants into a shared enum or constants class |
| **1.6** | 🟢 **Nitpick** | [`EntityUserAccessDto.java:37`](src/main/java/com/af/novadesk/api/finance/dto/EntityUserAccessDto.java:37) | The `@Pattern` regex is duplicated from validation logic. Consider extracting to a constant for testability. | Extract `ENTITY_ROLE_PATTERN` constant |

---

### Epic 2: Transactional Outbox Pattern (Employee Events)

#### Files

| File | Status | Lines |
|------|--------|-------|
| [`src/main/resources/db/migration/V1.70__create_pr_employee_outbox_events_table.sql`](src/main/resources/db/migration/V1.70__create_pr_employee_outbox_events_table.sql) | **New** | 38 |
| [`src/main/java/com/af/novadesk/api/payroll/entity/EmployeeOutboxEvent.java`](src/main/java/com/af/novadesk/api/payroll/entity/EmployeeOutboxEvent.java) | **New** | 86 |
| [`src/main/java/com/af/novadesk/api/payroll/repository/EmployeeOutboxEventRepository.java`](src/main/java/com/af/novadesk/api/payroll/repository/EmployeeOutboxEventRepository.java) | **New** | 24 |
| [`src/main/java/com/af/novadesk/api/payroll/service/EmployeeOutboxService.java`](src/main/java/com/af/novadesk/api/payroll/service/EmployeeOutboxService.java) | **New** | 34 |
| [`src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java) | **New** | 131 |

#### ✅ Strengths

1. **Correct outbox pattern** — Events are persisted in the same `@Transactional` boundary as the Employee CRUD ([`EmployeeServiceImpl`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:181)), ensuring atomicity.

2. **Idempotency key** — The [`idempotency_key`](src/main/resources/db/migration/V1.70__create_pr_employee_outbox_events_table.sql:18) has a UNIQUE constraint, and consumers can safely retry without double-processing.

3. **Proper indexing** — The [`idx_emp_outbox_status`](src/main/resources/db/migration/V1.70__create_pr_employee_outbox_events_table.sql:30) index on `(outbox_status, created_at)` supports efficient polling by consumer services.

4. **JSONB payload** — Using JSONB ([`EmployeeOutboxEvent.java:49`](src/main/java/com/af/novadesk/api/payroll/entity/EmployeeOutboxEvent.java:49)) enables flexible payload evolution without schema changes.

#### ⚠️ Issues & Recommendations

| # | Severity | File | Issue | Recommendation |
|---|----------|------|-------|---------------|
| **2.1** | 🟡 **Concern** | [`EmployeeOutboxServiceImpl.java:35`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java:35) | Payload is constructed with `String.format()` using a JSON text block. This is fragile: if any field contains a double-quote (`"`), the JSON breaks. The `safeString()` helper on line 128 handles this, but `employee.getId()` (UUID) on line 50 is embedded without escaping. | Use a proper JSON serialization library (e.g., Jackson ObjectMapper) to build the payload. Alternatively, use `JsonNode` or a Map serialized to JSON. |
| **2.2** | 🟡 **Concern** | [`EmployeeOutboxServiceImpl.java:82`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java:82) | `createRoleChangedEvent()` uses `System.currentTimeMillis()` in the idempotency key (`eventType + ":" + employee.getId() + ":" + System.currentTimeMillis()`). This defeats the purpose of idempotency — every call generates a unique key. If the same role change is retried, a new event will be created. | Use a deterministic key like `EMPLOYEE_ROLE_CHANGED:<employeeId>:<managerUuid-before>:<managerUuid-after>` to make retries idempotent. |
| **2.3** | 🟢 **Nitpick** | [`EmployeeOutboxServiceImpl.java:35-61`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java:35-61) | High code duplication between `createOnboardedEvent()` and `createRoleChangedEvent()`. The only differences are `eventType`, `idempotencyKey`, and the payload is identical. | Extract the common payload + entity building into a private helper method that takes `eventType` and `idempotencyKey` as parameters. |
| **2.4** | 🟢 **Nitpick** | [`EmployeeOutboxEvent.java:80`](src/main/java/com/af/novadesk/api/payroll/entity/EmployeeOutboxEvent.java:80) | `createdAt` uses `LocalDateTime.now()` (JVM default timezone) rather than `Instant.now()` or database default. If the application server and database are in different timezones, timestamps will drift. | Use `Instant.now()` with `Column(columnDefinition = "TIMESTAMPTZ")` or rely on the DB's `DEFAULT NOW()` as defined in the migration. |

---

### Epic 3: Payroll Security Configuration

#### Files

| File | Status | Lines |
|------|--------|-------|
| [`src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java) | **New** | 296 |

#### ✅ Strengths

1. **Dedicated security chain** — The `@Order(2)` filter chain scoped to `/api/v1/payroll/**` properly separates Payroll security from Finance and catch-all chains.

2. **Audience validation** — The [`JwtAudienceValidator`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:244) prevents token replay attacks across services.

3. **Roles extraction** — The addition of `roles` claim extraction (line 161) enables `hasRole()` annotations in addition to `hasAuthority()`, which was a gap identified in [`leave-pending-role-based-access.md`](plans/leave-pending-role-based-access.md:34).

4. **CORS configuration** — Properly handles wildcard vs. specific origins with `allowCredentials` logic.

5. **Custom JwtDecoderDecorator** — Fallback for non-Nimbus decoders ensures robustness.

#### ⚠️ Issues & Recommendations

| # | Severity | File | Issue | Recommendation |
|---|----------|------|-------|---------------|
| **3.1** | 🔴 **Bug** | [`PayrollSecurityConfig.java:103`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:103) | `.oauth2ResourceServer()` is configured but there is **no exception handling for JWT validation failures** returned by the `JwtDecoder`. If the JWT is expired, malformed, or has an invalid signature, the default Spring Security error response is returned (HTML or basic JSON), which may not match the application's error response format. | Add a custom `AuthenticationEntryPoint` that handles `JwtException`/`JwtValidationException` and returns a consistent JSON response format matching the app's error structure. |
| **3.2** | 🟡 **Concern** | [`PayrollSecurityConfig.java:184`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:184) | `allowAll = "*".equals(allowedOrigins)` — when `allowedOrigins` defaults to `*`, the code uses `addAllowedOriginPattern("*")` with `allowCredentials = false`. However, if a specific origin is set, credentials are allowed. This is correct but the comment should warn that `*` disables credentials (cookies/Authorization header) per the CORS spec. | Consider documenting why credentials are disabled with wildcard origins |
| **3.3** | 🟢 **Nitpick** | [`PayrollSecurityConfig.java:217`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:217) | The audience validation error message on line 261 includes the expected audience in plain text. If this is ever exposed in a production error response, it leaks configuration. | Use a generic error message like "The required audience is missing" |
| **3.4** | 🟢 **Nitpick** | [`PayrollSecurityConfig.java:112-123`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:112-123) | Authentication and access denied handlers use `getWriter().write()` with hardcoded JSON. Consider using `ObjectMapper` and a shared error DTO for consistency across modules. | Extract to a shared error response builder |

---

### Epic 4: Leave Auto-Expiry Scheduled Job

#### Files

| File | Status | Lines |
|------|--------|-------|
| [`plans/leave-auto-expiry-plan.md`](plans/leave-auto-expiry-plan.md) | **New** | 329 |
| [`src/main/java/com/af/novadesk/api/payroll/constants/LeaveRequestStatus.java`](src/main/java/com/af/novadesk/api/payroll/constants/LeaveRequestStatus.java) | Modified | +`EXPIRED` |
| [`src/main/java/com/af/novadesk/api/payroll/constants/LeaveRequestEventType.java`](src/main/java/com/af/novadesk/api/payroll/constants/LeaveRequestEventType.java) | Modified | +`LEAVE_EXPIRED` |
| [`src/main/java/com/af/novadesk/api/payroll/repository/LeaveRequestRepository.java`](src/main/java/com/af/novadesk/api/payroll/repository/LeaveRequestRepository.java:27) | Modified | +`findByStatusesAndStartDateBefore()` |
| [`src/main/java/com/af/novadesk/api/payroll/service/LeaveRequestService.java`](src/main/java/com/af/novadesk/api/payroll/service/LeaveRequestService.java:34) | Modified | +`expireLeaveRequest()` |
| [`src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java`](src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java) | Modified | +Expiry logic + helpers |
| [`src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java) | **New** | 97 |

#### ✅ Strengths

1. **Handles the bug** — The plan correctly identifies that [`cancelLeaveRequest()`](src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java:227) blocks cancellation ≤2 days before start, which means stale PENDING requests have no resolution path. The expiry job solves this.

2. **Correct balance restoration** — The [`expireLeaveRequest()`](src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java:305) method calls `restorePendingDays()` which correctly moves `pendingDays` back to `availableDays`.

3. **Per-request error handling** — The [`LeaveExpiryJob`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java:81-91) processes each request in a try-catch, preventing a single failure from blocking the entire batch.

4. **Comprehensive plan** — [`leave-auto-expiry-plan.md`](plans/leave-auto-expiry-plan.md) is thorough with flow diagrams, alternative analysis, and edge case coverage.

#### ⚠️ Issues & Recommendations

| # | Severity | File | Issue | Recommendation |
|---|----------|------|-------|---------------|
| **4.1** | 🟡 **Concern** | [`LeaveExpiryJob.java:51`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java:51) | The `@Transactional` annotation on the job method (`expireStaleLeaveRequests()`) wraps the entire batch. If one expiry fails after others have succeeded, the entire transaction rolls back, undoing successful expirations. However, the per-request try-catch in the loop suggests requests should be individually transactional. | Either add `@Transactional(propagation = Propagation.REQUIRES_NEW)` on the `expireLeaveRequest()` service method, or call `LeaveRequestRepository.save()` directly in the job with per-request transactions. |
| **4.2** | 🟢 **Nitpick** | [`LeaveRequestServiceImpl.java:341`](src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java:341) | `initializeLeaveBalances()` returns `null` with a comment "Re-initialization handled by scheduled job". This is a no-op that could surprise callers. | Either implement properly or throw `UnsupportedOperationException` with a clear message |

---

### Epic 5: Role-Based Access for Leave Pending

#### Files

| File | Status | Lines |
|------|--------|-------|
| [`plans/leave-pending-role-based-access.md`](plans/leave-pending-role-based-access.md) | **New** | 299 |
| [`src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java) | Modified | +Role-based access logic |
| [`src/main/java/com/af/novadesk/api/payroll/api/LeaveRequestApi.java`](src/main/java/com/af/novadesk/api/payroll/api/LeaveRequestApi.java) | Modified | Changed `approverId` from required to optional |

#### ✅ Strengths

1. **Graceful role hierarchy** — The [`listPending()`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:94) method implements a clear fallback chain: direct approver → SUPER_ADMIN → entity MANAGER → error.

2. **No cross-module JPA import** — The [`LeaveRequestController`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:117) queries `EntityUserAccess` via its repository, but this is acceptable as the Finance module's repository is injected (an existing violation). The alternative (outbox-based) would be over-engineered for this read-only check.

3. **JWT claim extraction helpers** — [`getRolesFromJwt()`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:53) and [`getAuthUserIdFromJwt()`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:65) are cleanly extracted.

#### ⚠️ Issues & Recommendations

| # | Severity | File | Issue | Recommendation |
|---|----------|------|-------|---------------|
| **5.1** | 🟢 **Nitpick** | [`LeaveRequestController.java:39`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:39) | The three JWT helper methods (`getOrganizationIdFromJwt`, `getRolesFromJwt`, `getAuthUserIdFromJwt`) duplicate `SecurityContextHolder.getContext().getAuthentication()` and `Jwt` casting. This is repetitive code scattered across the controller. | Extract a single `getJwt()` helper method that returns the `Jwt` instance, then derive claims from it. Or inject `JwtAuthenticationToken` directly via Spring Security's principal resolution. |
| **5.2** | 🟢 **Nitpick** | [`LeaveRequestController.java:102-103`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:102-103) | `isSuperAdmin` check uses `SUPER_ADMIN` literal but `PayrollSecurityConfig` maps roles with `ROLE_` prefix (line 164). The controller reads from the raw JWT `roles` claim, not from Spring Security's `GrantedAuthority`. This is correct (different sources) but could confuse future maintainers. | Add a Javadoc comment explaining that JWT `roles` claim is used directly (not `ROLE_`-prefixed authorities) to avoid confusion. |

---

### Plan Documents (Design Documentation)

| File | Description |
|------|-------------|
| [`plans/frontend-prompt-for-reversed-onboarding.md`](plans/frontend-prompt-for-reversed-onboarding.md) | Frontend API contract for reversed onboarding |
| [`plans/cm-employee-sync-plan.md`](plans/cm-employee-sync-plan.md) | Design for syncing pr_employees → cm_employees (future work) |
| [`plans/manager-role-onboarding-plan.md`](plans/manager-role-onboarding-plan.md) | Design for manager role toggle |
| [`plans/leave-pending-role-based-access.md`](plans/leave-pending-role-based-access.md) | Design for role-based pending leave access |
| [`plans/leave-auto-expiry-plan.md`](plans/leave-auto-expiry-plan.md) | Design for auto-expiry of stale leave requests |

These plan documents are well-written with clear problem statements, architecture diagrams, API contracts, and edge case analysis. They serve as valuable documentation for future maintainers.

---

## Cross-Cutting Concerns

### Security

1. **🔴 JWT exception handling gap** — See issue **3.1**. Without explicit handling of `JwtValidationException`, the app may return inconsistent or HTML error responses.

2. **🟢 Token forwarding safety** — [`AuthHubClientService`](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java:85) forwards the current JWT to af-authhub. This is secure because the same token was already validated by Spring Security. However, consider adding a timeout or circuit breaker to prevent cascading failures if af-authhub is down.

### Data Integrity

3. **🟡 Outbox + ApplicationEvent dual emission** — [`EmployeeServiceImpl`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:181-195) both persists an outbox row AND publishes a Spring `ApplicationEvent`. The `ApplicationEvent` is handled by [`EntityAccessSyncService`](src/main/java/com/af/novadesk/api/finance/service/impl/EntityAccessSyncService.java) with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. This means there are **two paths** to grant entity access: (1) the in-memory event, and (2) a future outbox consumer. If both fire, the `DuplicateUserAccessException` catch on line 55 handles it, but this dual-path design is confusing. Consider if both are needed, or if one path (outbox-only) should handle everything.

### Code Quality

4. **🟡 Bug in `getEmployeeByCode()`** — See issue **1.1**. This is a blocking bug that will cause employee code lookups to fail with `EmployeeNotFoundException`.

5. **🟡 Payload serialization fragility** — See issue **2.1**. Manual JSON construction is prone to escaping bugs. Use ObjectMapper.

6. **🟡 Non-idempotent idempotency key** — See issue **2.2**. The `System.currentTimeMillis()` approach for role change events defeats the UNIQUE constraint on `idempotency_key`.

7. **🟢 Code duplication in outbox service** — See issue **2.3**. Extract shared logic.

8. **🟢 Duplicate JWT extraction helpers** — See issue **5.1**. Extract shared JWT helper.

### Testing

No test files were included in this PR for the new or modified functionality. The following should have tests:

| Function | Suggested Test Coverage |
|----------|----------------------|
| [`EmployeeServiceImpl.onboardEmployee()`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:78) | Both legacy and reversed flows, duplicate email, AuthHub failure, manager role toggle |
| [`AuthHubClientService.createUser()`](src/main/java/com/af/novadesk/api/payroll/service/AuthHubClientService.java:69) | 2xx success, 409 conflict (idempotent), 5xx error, RestClientException |
| [`EntityAccessSyncService.onEmployeeOnboarded()`](src/main/java/com/af/novadesk/api/finance/service/impl/EntityAccessSyncService.java:41) | Successful grant, duplicate access (idempotent) |
| [`LeaveExpiryJob.expireStaleLeaveRequests()`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java:53) | Empty results, single expiry, batch with one failure |
| [`LeaveRequestServiceImpl.expireLeaveRequest()`](src/main/java/com/af/novadesk/api/payroll/service/impl/LeaveRequestServiceImpl.java:295) | Happy path, already approved, invalid state |
| [`LeaveRequestController.listPending()`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:94) | Approver path, SUPER_ADMIN path, MANAGER path, missing params |
| [`PayrollSecurityConfig`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java) | JWT with valid/invalid audience, missing permissions, CORS header verification |

---

## ⭐ Overall Assessment

### What's Done Well

- **Excellent modular architecture** — Cross-module communication via shared events in the `common` package is clean and maintainable
- **Idempotency-first design** — AuthHub calls, outbox events, and EntityUserAccess grants are all idempotent
- **Comprehensive documentation** — Every feature has a detailed plan document with flow diagrams and API contracts
- **Backward compatible** — Legacy onboarding flow preserved alongside the new reversed flow
- **Defensive programming** — Per-request error handling in scheduled jobs, null-safe lazy loading in mappers

### Critical (Must Fix Before Merge)

| # | Issue | File |
|---|-------|------|
| 1 | 🔴 **`getEmployeeByCode()` calls wrong repository method** — line 244 passes `null` for `authUserId` | [`EmployeeServiceImpl.java:244`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:244) |
| 2 | 🔴 **No JWT validation exception handler** — `oauth2ResourceServer()` without error handling returns inconsistent responses | [`PayrollSecurityConfig.java:103`](src/main/java/com/af/novadesk/api/payroll/config/PayrollSecurityConfig.java:103) |

### Important (Should Fix)

| # | Issue | File |
|---|-------|------|
| 3 | 🟡 **Manual JSON payload construction** — Use ObjectMapper instead of `String.format()` | [`EmployeeOutboxServiceImpl.java:35`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java:35) |
| 4 | 🟡 **Non-idempotent role change events** — Remove `System.currentTimeMillis()` from idempotency key | [`EmployeeOutboxServiceImpl.java:82`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java:82) |
| 5 | 🟡 **Batch transaction scope** — All expired requests in one transaction; one failure rolls back all | [`LeaveExpiryJob.java:51`](src/main/java/com/af/novadesk/api/payroll/scheduler/LeaveExpiryJob.java:51) |

### Nitpicks (Consider Fixing)

| # | Issue | File |
|---|-------|------|
| 6 | 🟢 Outbox service code duplication | [`EmployeeOutboxServiceImpl.java`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeOutboxServiceImpl.java) |
| 7 | 🟢 Duplicate JWT extraction helpers in controller | [`LeaveRequestController.java:39-71`](src/main/java/com/af/novadesk/api/payroll/controller/LeaveRequestController.java:39-71) |
| 8 | 🟢 Hardcoded role strings | [`EmployeeServiceImpl.java:162`](src/main/java/com/af/novadesk/api/payroll/service/impl/EmployeeServiceImpl.java:162) |
| 9 | 🟢 No unit tests for new/modified code | Throughout |

---

## Files Summary

**Total uncommitted/unversioned files identified:** ~36 files across 5 epics

- **New files:** `AuthHubClientService.java`, `EmployeeOnboardedEvent.java`, `EntityAccessSyncService.java`, `EmployeeOutboxEvent.java`, `EmployeeOutboxService.java`, `EmployeeOutboxServiceImpl.java`, `PayrollSecurityConfig.java`, `LeaveExpiryJob.java`, 2 SQL migrations, 5 plan documents
- **Modified files:** `Employee.java`, `EmployeeDto.java`, `EmployeeMapper.java`, `EmployeeServiceImpl.java`, `EmployeeService.java`, `EntityUserAccessDto.java`, `LeaveRequestStatus.java`, `LeaveRequestEventType.java`, `LeaveRequestRepository.java`, `LeaveRequestService.java`, `LeaveRequestServiceImpl.java`, `LeaveRequestController.java`, `LeaveRequestApi.java`
