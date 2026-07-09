package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.dto.ReinstateEmployeeRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for Employee management (LLR-PAY-01.1).
 */
public interface EmployeeService {

    /**
     * Onboards a new employee — creates Employee record and auto-creates
     * LeaveBalance records for all three leave types (LLR-PAY-01.1).
     */
    EmployeeDto onboardEmployee(EmployeeDto request);

    /**
     * Re-onboards a previously offboarded employee — reactivates the existing
     * record, creates a new AuthHub user, and sends a fresh password-setup
     * invitation email.
     */
    EmployeeDto reonboardEmployee(EmployeeDto request);

    /**
     * Reinstates a previously offboarded employee by ID — creates a fresh AuthHub user,
     * sends a new invitation email, reactivates the CmEmployee record, and reactivates
     * (or creates) the entity assignment for the specified legal entity.
     */
    EmployeeDto reinstateEmployee(UUID employeeId, ReinstateEmployeeRequest request);

    EmployeeDto getEmployee(UUID employeeId);

    EmployeeDto getEmployeeByAuthUserAndEntity(UUID authUserId, UUID legalEntityId);

    EmployeeDto getEmployeeByCode(String employeeCode, UUID legalEntityId);

    EmployeeDto updateEmployee(UUID employeeId, EmployeeDto request);

    /**
     * Offboards (soft-deletes) an employee — calls AuthHub to revoke tokens
     * and deactivate the user account, sets employee status to OFFBOARDED,
     * terminates all entity assignments, and deactivates payroll details.
     * Data is preserved for audit/reporting.
     */
    void terminateEmployee(UUID employeeId, LocalDate terminationDate);

    List<EmployeeDto> listAllEmployees();

    List<EmployeeDto> listEmployeesByEntity(UUID legalEntityId);

    /**
     * List employees filtered by status and optionally scoped to a legal entity.
     * Use this to get PENDING_SETUP, ACTIVE, INACTIVE, or OFFBOARDED employees.
     *
     * @param status        the employee status to filter by
     * @param legalEntityId optional legal entity scope (null = org-wide)
     * @return filtered employee list
     */
    List<EmployeeDto> listEmployeesByStatus(EmployeeStatus status, UUID legalEntityId);

    List<EmployeeDto> listEmployeesByManager(UUID managerId);

    PageResponse<EmployeeDto> listEmployeesFiltered(
            String q,
            UUID legalEntityId,
            EmployeeStatus status,
            UUID managerId,
            int page,
            int size,
            String sortBy,
            String sortDir);

    /**
     * Returns the Employee record for the currently authenticated user.
     * Auto-transitions from PENDING_SETUP to ACTIVE on first access (first login).
     *
     * @param legalEntityId the legal entity to scope the lookup
     * @return the matching EmployeeDto
     */
    EmployeeDto getCurrentEmployee(UUID legalEntityId);

    /**
     * Activates a PENDING_SETUP employee — transitions employeeStatus to ACTIVE.
     * Called when af-authhub confirms the employee has set their password
     * (consumed via RabbitMQ {@code USER_PASSWORD_SET} event).
     *
     * @param authUserId the AuthHub user UUID that completed password setup
     * @param orgId      the organization ID
     */
    void activateEmployee(UUID authUserId, UUID orgId);

    /**
     * Moves an employee from one legal entity to another by deactivating the
     * current entity assignment and creating/activating an assignment in the
     * target entity.
     *
     * <p>Preserves the employee's identity (authUserId, employeeCode) and
     * all historical data (payroll, leave, assets).</p>
     *
     * @param employeeId   the employee to move
     * @param fromEntityId the source legal entity
     * @param toEntityId   the target legal entity
     * @param makePrimary  whether to mark the target as the primary entity
     * @return the updated EmployeeDto scoped to the target entity
     * @throws com.af.novadesk.api.common.exception.EmployeeNotFoundException if the employee or target entity is not found
     * @throws com.af.novadesk.api.payroll.exception.InvalidEmployeeStateException if the move cannot be performed
     */
    EmployeeDto moveEmployee(UUID employeeId, UUID fromEntityId, UUID toEntityId, boolean makePrimary);

    /**
     * Hard-deletes an employee and all associated data from the system.
     * <p>
     * This is a permanent, irreversible delete that:
     * <ul>
     *   <li>Checks the asset offboarding gate — blocks with {@code AssetOffboardingNotClearException}
     *       if the employee still has unreturned assets</li>
     *   <li>Calls AuthHub to permanently delete the user, profile, and all auth data</li>
     *   <li>Deletes the ShadowUser record from the local database</li>
     *   <li>Deletes all entity assignments, payroll details, leave data, and outbox events</li>
     *   <li>Deletes the CmEmployee record itself</li>
     * </ul>
     * </p>
     *
     * @param employeeId the employee to permanently delete
     * @throws com.af.novadesk.api.common.exception.EmployeeNotFoundException if the employee is not found
     * @throws com.af.novadesk.api.payroll.exception.AssetOffboardingNotClearException if the employee still has unreturned assets
     * @throws com.af.novadesk.api.common.exception.AuthHubIntegrationException if the AuthHub delete call fails
     */
    void hardDeleteEmployee(UUID employeeId);
}
