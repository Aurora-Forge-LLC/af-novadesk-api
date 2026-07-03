package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.dto.MoveEmployeeRequest;
import com.af.novadesk.api.payroll.dto.ReinstateEmployeeRequest;
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

@Tag(name = "Payroll - Employees", description = "Employee lifecycle management in the Payroll module")
@RequestMapping("/api/v1/payroll/employees")
@SecurityRequirement(name = "bearerAuth")
public interface EmployeeApi {

    @Operation(summary = "Onboard employee")
    @PostMapping
    @PreAuthorize("hasAuthority('employees:onboard') or hasAuthority('employees:manage')")
    ResponseEntity<ApiResponse<EmployeeDto>> onboardEmployee(@Valid @RequestBody EmployeeDto request);

    @Operation(summary = "Re-onboard previously offboarded employee")
    @PostMapping("/re-onboard")
    @PreAuthorize("hasAuthority('employees:onboard') or hasAuthority('employees:manage')")
    ResponseEntity<ApiResponse<EmployeeDto>> reonboardEmployee(@Valid @RequestBody EmployeeDto request);

    @Operation(summary = "Reinstate a previously offboarded employee",
            description = "Reactivates an offboarded employee by ID. Creates a fresh AuthHub user, "
                        + "sets status to PENDING_SETUP, sends a new password-setup invitation email, "
                        + "and reactivates (or creates) the entity assignment for the given legal entity.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Employee reinstated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or employee not in OFFBOARDED status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Employee or legal entity not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AuthHub integration failed")
    })
    @PostMapping("/{id}/reinstate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> reinstateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody ReinstateEmployeeRequest request);

    @Operation(summary = "List employees",
            description = "List employees optionally filtered by legal entity and/or status. "
                        + "Status values: PENDING_SETUP (invited, no password), ACTIVE, INACTIVE, OFFBOARDED.")
    @GetMapping
    @PreAuthorize("hasAuthority('employees:read')")
    ResponseEntity<ApiResponse<List<EmployeeDto>>> listEmployees(
            @Parameter(description = "Legal entity ID to scope the employee list")
            @RequestParam(required = false) UUID legalEntityId,
            @Parameter(description = "Filter by employee status (ACTIVE, TERMINATED, etc.)")
            @RequestParam(required = false) String status);

    @Operation(summary = "Get current employee profile (self-service)")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<EmployeeDto>> getCurrentEmployee(
            @Parameter(hidden = true) @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "Get employee by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employees:read')")
    ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(@PathVariable UUID id);

    @Operation(summary = "Update employee details")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('employees:write') or hasAuthority('employees:manage')")
    ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(
            @PathVariable UUID id, @Valid @RequestBody EmployeeDto request);

    @Operation(summary = "Move employee to another entity")
    @PostMapping("/{id}/move")
    @PreAuthorize("hasAuthority('employees:write') or hasAuthority('employees:manage')")
    ResponseEntity<ApiResponse<EmployeeDto>> moveEmployee(
            @PathVariable UUID id, @Valid @RequestBody MoveEmployeeRequest request);

    @Operation(summary = "Terminate employee")
    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('employees:offboard') or hasAuthority('employees:manage')")
    ResponseEntity<ApiResponse<Void>> terminateEmployee(
            @PathVariable UUID id, @RequestParam String terminationDate);

    @Operation(summary = "Hard-delete employee record")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employees:delete')")
    ResponseEntity<ApiResponse<Void>> hardDeleteEmployee(@PathVariable UUID id);
}
