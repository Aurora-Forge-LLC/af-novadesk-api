package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.asset.dto.OffboardingAssetCheckDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Employee-scoped asset queries.
 * Base path: {@code /api/v1/employees/{employeeId}/assets}
 */
@Tag(name = "Employee Assets", description = "Asset queries scoped to an employee — self-service portal and offboarding gate")
@RequestMapping("/api/v1/employees/{employeeId}/assets")
@SecurityRequirement(name = "bearerAuth")
public interface AssetEmployeeApi {

    @Operation(summary = "List assets currently assigned to an employee", description = "LLR-AST-02.4")
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<List<AssetAssignmentDto>>> listByEmployee(@PathVariable UUID employeeId);

    @Operation(summary = "Offboarding asset gate check", description = "LLR-AST-03.2 — returns cleared=true only when employee has no unreturned assets.")
    @GetMapping("/offboarding-check")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<OffboardingAssetCheckDto>> offboardingCheck(@PathVariable UUID employeeId);
}
