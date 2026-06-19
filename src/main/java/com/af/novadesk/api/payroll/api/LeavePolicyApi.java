package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.LeavePolicyDto;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;
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

/**
 * REST API for leave policy management — the configuration layer of the
 * Leave Policy Rule Engine. SUPER_ADMIN (org-level) and MANAGER (entity-level)
 * users can create, update, list, and delete leave policies per entity.
 */
@Tag(name = "Payroll - Leave Policies", description = "Leave policy management — configurable rule engine")
@RequestMapping("/api/v1/payroll/leave-policies")
@SecurityRequirement(name = "bearerAuth")
public interface LeavePolicyApi {

    @Operation(summary = "Create leave policy",
            description = "Creates a new leave policy for an entity. Automatically generates "
                    + "leave balance sheets for all active employees in the entity.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Policy created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Policy name already exists")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LeavePolicyDto>> createPolicy(@Valid @RequestBody LeavePolicyRequest request);

    @Operation(summary = "Update leave policy",
            description = "Updates allocation, earning rules, and borrowing limits. "
                    + "Name and payment type are immutable after creation.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LeavePolicyDto>> updatePolicy(
            @PathVariable UUID id, @Valid @RequestBody LeavePolicyRequest request);

    @Operation(summary = "Delete leave policy", description = "Soft-deletes a leave policy.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable UUID id);

    @Operation(summary = "Get leave policy by ID")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<LeavePolicyDto>> getPolicy(@PathVariable UUID id);

    @Operation(summary = "List leave policies",
            description = "Lists policies. If legalEntityId is provided, filters by entity. "
                    + "Otherwise returns all policies for the caller's organization.")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeavePolicyDto>>> listPolicies(
            @Parameter(description = "Optional legal entity ID to filter by")
            @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "List leave policies by entity (path-variable alias)")
    @GetMapping("/entity/{legalEntityId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LeavePolicyDto>>> listPoliciesByEntity(@PathVariable UUID legalEntityId);
}
