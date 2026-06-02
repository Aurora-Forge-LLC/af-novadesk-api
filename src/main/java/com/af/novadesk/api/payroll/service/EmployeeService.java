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

    List<EmployeeDto> listEmployeesByEntity(UUID legalEntityId);

    List<EmployeeDto> listEmployeesByManager(UUID managerId);
}
