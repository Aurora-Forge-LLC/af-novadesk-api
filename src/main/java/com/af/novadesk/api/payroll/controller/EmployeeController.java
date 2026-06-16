package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.EmployeeApi;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.dto.MoveEmployeeRequest;
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
        return ResponseBuilder.created(result, "Employee onboarded successfully. "
                + "An invitation email has been sent.");
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> reonboardEmployee(@Valid EmployeeDto request) {
        EmployeeDto result = employeeService.reonboardEmployee(request);
        return ResponseBuilder.created(result, "Employee re-onboarded successfully. "
                + "A new invitation email has been sent.");
    }

    @Override
    public ResponseEntity<ApiResponse<List<EmployeeDto>>> listEmployees(UUID legalEntityId, String status) {
        List<EmployeeDto> result;
        if (status != null && !status.isBlank()) {
            EmployeeStatus employeeStatus = EmployeeStatus.valueOf(status.toUpperCase());
            result = employeeService.listEmployeesByStatus(employeeStatus, legalEntityId);
        } else if (legalEntityId != null) {
            result = employeeService.listEmployeesByEntity(legalEntityId);
        } else {
            result = employeeService.listAllEmployees();
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> getCurrentEmployee(UUID legalEntityId) {
        EmployeeDto result = employeeService.getCurrentEmployee(legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(UUID id) {
        EmployeeDto result = employeeService.getEmployee(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(UUID id, EmployeeDto request) {
        EmployeeDto result = employeeService.updateEmployee(id, request);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_UPDATED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> moveEmployee(UUID id, MoveEmployeeRequest request) {
        EmployeeDto result = employeeService.moveEmployee(
                id, request.getFromEntityId(), request.getToEntityId(), request.isMakePrimary());
        return ResponseBuilder.ok(result, "Employee moved successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> terminateEmployee(UUID id, String terminationDate) {
        employeeService.terminateEmployee(id, LocalDate.parse(terminationDate));
        return ResponseBuilder.ok(null, "Employee offboarded successfully. "
                + "Access has been revoked.");
    }
}
