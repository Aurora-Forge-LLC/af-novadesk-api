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

@Tag(name = "Payroll - Employees", description = "Employee onboarding, offboarding, and lifecycle management")
@RequestMapping("/api/v1/payroll/employees")
@SecurityRequirement(name = "bearerAuth")
public interface EmployeeApi {

    @Operation(summary = "Onboard employee",
            description = "Creates a new employee with PENDING_SETUP status. "
                        + "Provisions a passwordless user in AuthHub (EMPLOYEE role) "
                        + "and sends an invitation email. Requires `email`, `first_name`, `last_name`. "
                        + "For re-onboarding a previously offboarded employee, use POST /re-onboard instead.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Employee onboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate or previously offboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AuthHub integration failed")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> onboardEmployee(@Valid @RequestBody EmployeeDto request);

    @Operation(summary = "Re-onboard a previously offboarded employee",
            description = "Reactivates an offboarded employee — creates a fresh AuthHub user, "
                        + "sets status to PENDING_SETUP, and sends a new password-setup invitation email. "
                        + "Use this when a former employee rejoins the organization.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Employee re-onboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Employee is not offboarded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Employee not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "AuthHub integration failed")
    })
    @PostMapping("/re-onboard")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> reonboardEmployee(@Valid @RequestBody EmployeeDto request);

    @Operation(summary = "List employees",
            description = "List employees optionally filtered by legal entity and/or status. "
                        + "Status values: PENDING_SETUP (invited, no password), ACTIVE, INACTIVE, OFFBOARDED.")
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<EmployeeDto>>> listEmployees(
            @Parameter(description = "Optional legal entity ID filter")
            @RequestParam(required = false) UUID legalEntityId,
            @Parameter(description = "Optional status filter: PENDING_SETUP, ACTIVE, INACTIVE, OFFBOARDED")
            @RequestParam(required = false) String status);

    @Operation(summary = "Get current employee (self-service)",
            description = "Returns the Employee record for the currently authenticated user. "
                        + "Auto-transitions from PENDING_SETUP to ACTIVE on first access.")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<EmployeeDto>> getCurrentEmployee(
            @Parameter(description = "Legal entity ID (from frontend's active entity context)")
            @RequestParam UUID legalEntityId);

    @Operation(summary = "Get employee by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> getEmployee(@PathVariable UUID id);

    @Operation(summary = "Update employee")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<EmployeeDto>> updateEmployee(
            @PathVariable UUID id, @RequestBody EmployeeDto request);

    @Operation(summary = "Offboard employee (soft-delete)",
            description = "Offboards an employee — calls AuthHub to revoke tokens/deactivate account, "
                        + "sets status to OFFBOARDED, terminates all entity assignments. "
                        + "Data is preserved for audit. Employee cannot access the system after offboarding.")
    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> terminateEmployee(
            @PathVariable UUID id, @RequestParam String terminationDate);
}
