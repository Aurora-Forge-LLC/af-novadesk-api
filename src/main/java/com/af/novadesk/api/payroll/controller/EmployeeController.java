package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.EmployeeApi;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class EmployeeController implements EmployeeApi {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> onboardEmployee(@Valid EmployeeDto request) {
        EmployeeDto result = employeeService.onboardEmployee(request);
        return ResponseBuilder.created(result, "Employee onboarded successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> listEmployees(UUID legalEntityId) {
        List<EmployeeDto> result = (legalEntityId != null)
                ? employeeService.listEmployeesByEntity(legalEntityId)
                : employeeService.listAllEmployees();
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(UUID id) {
        EmployeeDto result = employeeService.getEmployee(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(UUID id, @Valid EmployeeDto request) {
        EmployeeDto result = employeeService.updateEmployee(id, request);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_UPDATED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> terminateEmployee(UUID id, String terminationDate) {
        employeeService.terminateEmployee(id, LocalDate.parse(terminationDate));
        return ResponseBuilder.ok(null, "Employee terminated");
    }
}
