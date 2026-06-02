package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Employees", description = "Employee onboarding and management — LLR-PAY-01.1")
@RequestMapping("/api/v1/payroll/employees")
@SecurityRequirement(name = "bearerAuth")
public interface EmployeeApi {

    @Operation(summary = "Onboard employee", description = "Creates an Employee record and auto-creates LeaveBalance records")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Employee onboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate employee")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> onboardEmployee(@Valid @RequestBody EmployeeDto request);

    @Operation(summary = "List employees by entity")
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<EmployeeDto>>> listEmployees(
            @Parameter(description = "Legal entity ID") @RequestParam UUID legalEntityId);

    @Operation(summary = "Get employee by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(@PathVariable UUID id);

    @Operation(summary = "Update employee")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(
            @PathVariable UUID id, @Valid @RequestBody EmployeeDto request);

    @Operation(summary = "Terminate employee")
    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> terminateEmployee(
            @PathVariable UUID id, @RequestParam String terminationDate);
}
