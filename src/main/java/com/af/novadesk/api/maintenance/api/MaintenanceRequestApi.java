package com.af.novadesk.api.maintenance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import com.af.novadesk.api.maintenance.dto.MaintenanceApprovalRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestCreateRequest;
import com.af.novadesk.api.maintenance.dto.MaintenanceRequestDto;
import com.af.novadesk.api.maintenance.dto.TechnicianAssignmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST contract for Asset Maintenance Requests.
 * Base path: {@code /api/v1/maintenance-requests}
 */
@Tag(name = "Maintenance Requests", description = "Employee-submitted asset repair/maintenance requests and the ops review workflow")
@RequestMapping("/api/v1/maintenance-requests")
@SecurityRequirement(name = "bearerAuth")
public interface MaintenanceRequestApi {

    @Operation(summary = "Submit a maintenance request for an asset assigned to the caller")
    @PostMapping
    @PreAuthorize("hasAuthority('maintenance:write') or hasAuthority('maintenance:manage')")
    ResponseEntity<ApiResponse<MaintenanceRequestDto>> create(@Valid @RequestBody MaintenanceRequestCreateRequest request);

    @Operation(summary = "Get a maintenance request by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('maintenance:read')")
    ResponseEntity<ApiResponse<MaintenanceRequestDto>> getById(@PathVariable UUID id);

    @Operation(summary = "List maintenance requests", description = "Filterable by asset and status.")
    @GetMapping
    @PreAuthorize("hasAuthority('maintenance:read')")
    ResponseEntity<ApiResponse<List<MaintenanceRequestDto>>> list(
            @Parameter(description = "Filter by asset")  @RequestParam(required = false) UUID              assetId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) MaintenanceStatus status);

    @Operation(summary = "Approve a submitted maintenance request", description = "Moves status to APPROVED and records the ops-estimated repair cost.")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('maintenance:manage')")
    ResponseEntity<ApiResponse<MaintenanceRequestDto>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceApprovalRequest request);

    @Operation(summary = "Reject a submitted maintenance request")
    @PostMapping("/{id}/reject")
    ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @RequestParam String reason);

    @Operation(summary = "Assign a technician to an approved maintenance request", description = "Moves status to IN_PROGRESS.")
    @PostMapping("/{id}/assign-technician")
    @PreAuthorize("hasAuthority('maintenance:manage')")
    ResponseEntity<ApiResponse<MaintenanceRequestDto>> assignTechnician(
            @PathVariable UUID id,
            @Valid @RequestBody TechnicianAssignmentRequest request);

    @Operation(summary = "Mark a maintenance request as completed")
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('maintenance:manage')")
    ResponseEntity<ApiResponse<MaintenanceRequestDto>> complete(
            @PathVariable UUID id,
            @RequestParam(required = false) String notes);
}
