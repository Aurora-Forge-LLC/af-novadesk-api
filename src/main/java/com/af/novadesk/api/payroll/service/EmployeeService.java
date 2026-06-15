package com.af.novadesk.api.payroll.service;

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

    EmployeeDto getEmployee(UUID employeeId);

    EmployeeDto getEmployeeByAuthUserAndEntity(UUID authUserId, UUID legalEntityId);

    EmployeeDto getEmployeeByCode(String employeeCode, UUID legalEntityId);

    EmployeeDto updateEmployee(UUID employeeId, EmployeeDto request);

    void terminateEmployee(UUID employeeId, LocalDate terminationDate);

    List<EmployeeDto> listAllEmployees();

    List<EmployeeDto> listEmployeesByEntity(UUID legalEntityId);

    List<EmployeeDto> listEmployeesByManager(UUID managerId);

    /**
     * Returns the Employee record for the currently authenticated user.
     *
     * <p>Reads the {@code authUserId} from the JWT {@code sub} claim and
     * looks up the matching Employee. Requires the legal entity context
     * to be explicitly provided (from the frontend's active entity).</p>
     *
     * @param legalEntityId the legal entity to scope the lookup
     * @return the matching EmployeeDto
     */
    EmployeeDto getCurrentEmployee(UUID legalEntityId);

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
}
