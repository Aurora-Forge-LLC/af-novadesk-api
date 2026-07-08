package com.af.novadesk.api.department.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.department.constants.DepartmentScope;
import com.af.novadesk.api.department.dto.DepartmentCreateRequest;
import com.af.novadesk.api.department.dto.DepartmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * REST contract for Department management.
 *
 * <p>Departments are organisational units (IT, HR, Finance, etc.) scoped to
 * either an organisation ({@code legalEntityId == null}) or a specific legal
 * entity. When creating a user or employee, the department is assigned via
 * {@code departmentId} — not to be confused with the entity role, which is a
 * separate concept (permissions vs. organisational unit).</p>
 *
 * <p>Base path: {@code /api/v1/departments}</p>
 */
@Tag(name = "Departments", description = "Department CRUD — organisational units for user/employee assignment")
@RequestMapping("/api/v1/departments")
@SecurityRequirement(name = "bearerAuth")
public interface DepartmentApi {

    // =========================================================================
    // Read
    // =========================================================================

    @Operation(
            summary = "List departments",
            description = "Returns the department list for the requested scope. " +
                          "scope=ORG returns the caller's organisation-level departments. " +
                          "scope=ENTITY requires legalEntityId and returns that entity's departments " +
                          "(the caller must have access to the entity)."
    )
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<DepartmentDto>>> listDepartments(
            @Parameter(description = "ORG or ENTITY") @RequestParam DepartmentScope scope,
            @Parameter(description = "Required when scope=ENTITY") @RequestParam(required = false) UUID legalEntityId);

    @Operation(
            summary = "Get department by ID",
            description = "Returns a single department by its ID. The caller must belong to the " +
                          "same organisation as the department."
    )
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<DepartmentDto>> getDepartment(
            @Parameter(description = "Department UUID") @PathVariable UUID id);

    // =========================================================================
    // Create
    // =========================================================================

    @Operation(
            summary = "Create a department",
            description = "Creates a new department. When legalEntityId is null, the department " +
                          "is created at the organisation level (requires ORG_ADMIN / ORG_HR). " +
                          "When set, it is scoped to that legal entity " +
                          "(requires ENTITY_ADMIN / HR_MANAGER on that entity). " +
                          "Duplicate names within the same scope are rejected."
    )
    @PostMapping
    @PreAuthorize("isAuthenticated()") // fine-grained check in DepartmentServiceImpl
    ResponseEntity<ApiResponse<DepartmentDto>> createDepartment(
            @Valid @RequestBody DepartmentCreateRequest request);

    // =========================================================================
    // Update
    // =========================================================================

    @Operation(
            summary = "Update a department",
            description = "Updates the name and/or scope of an existing department. " +
                          "Org-level: requires ORG_ADMIN / ORG_HR. " +
                          "Entity-level: requires ENTITY_ADMIN / HR_MANAGER on that entity."
    )
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // fine-grained check in DepartmentServiceImpl
    ResponseEntity<ApiResponse<DepartmentDto>> updateDepartment(
            @Parameter(description = "Department UUID") @PathVariable UUID id,
            @Valid @RequestBody DepartmentCreateRequest request);

    // =========================================================================
    // Delete
    // =========================================================================

    @Operation(
            summary = "Delete a department",
            description = "Deletes a department. Fails with DEP_IN_USE if any employee assignments " +
                          "or entity user access grants still reference this department. " +
                          "Org-level: requires ORG_ADMIN / ORG_HR. " +
                          "Entity-level: requires ENTITY_ADMIN / HR_MANAGER on that entity."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()") // fine-grained check in DepartmentServiceImpl
    ResponseEntity<ApiResponse<Void>> deleteDepartment(
            @Parameter(description = "Department UUID") @PathVariable UUID id);
}
