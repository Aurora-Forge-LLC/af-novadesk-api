package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.payroll.dto.EmployeeDto;

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

    /**
     * Returns the Employee record for the currently authenticated user.
     * Auto-transitions from PENDING_SETUP to ACTIVE on first access (first login).
     *
     * @param legalEntityId the legal entity to scope the lookup
     * @return the matching EmployeeDto
     */
    EmployeeDto getCurrentEmployee(UUID legalEntityId);
}
