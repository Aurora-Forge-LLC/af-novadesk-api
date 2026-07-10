package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.security.CallerContext;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.EmployeeApi;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.dto.MoveEmployeeRequest;
import com.af.novadesk.api.payroll.dto.ReinstateEmployeeRequest;
import com.af.novadesk.api.payroll.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EmployeeController implements EmployeeApi {

    private final EmployeeService employeeService;
    private final CallerContext   callerContext;

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
    public ResponseEntity<ApiResponse<EmployeeDto>> reinstateEmployee(UUID id, ReinstateEmployeeRequest request) {
        EmployeeDto result = employeeService.reinstateEmployee(id, request);
        return ResponseBuilder.ok(result, "Employee reinstated successfully. A new invitation email has been sent.");
    }

    @Override
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> listEmployees(
            UUID legalEntityId, EmployeeStatus status, String q, UUID managerId,
            int page, int size, String sortBy, String sortDir) {

        List<UUID> employeeIdIn = null;

        if (callerContext.canReadAllEmployees()) {
            // HR_MANAGER / ENTITY_ADMIN / ORG_HR — pass all filters through unchanged
        } else if (callerContext.isManager()) {
            // MANAGER — scope to direct reports; honour explicit managerId only if it's themselves
            if (managerId != null && !managerId.equals(callerContext.getEmployeeId())) {
                throw new IllegalArgumentException("Access denied: you may only list your own direct reports");
            }
            employeeIdIn = callerContext.getDirectReportIds();
        } else {
            // EMPLOYEE — own record only
            employeeIdIn = List.of(callerContext.getEmployeeId());
        }

        PageResponse<EmployeeDto> result = employeeService.listEmployeesFiltered(
                q, legalEntityId, status, managerId, employeeIdIn, page, size, sortBy, sortDir);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> getCurrentEmployee(UUID legalEntityId) {
        EmployeeDto result = employeeService.getCurrentEmployee(legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(UUID id) {
        if (!callerContext.canReadAllEmployees()) {
            if (!callerContext.isSelf(id) && !callerContext.isDirectReport(id)) {
                throw new IllegalArgumentException("Access denied: you do not have access to this employee record");
            }
        }
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

    @Override
    public ResponseEntity<ApiResponse<Void>> hardDeleteEmployee(UUID id) {
        employeeService.hardDeleteEmployee(id);
        return ResponseBuilder.noContent("Employee hard-deleted successfully.");
    }
}
