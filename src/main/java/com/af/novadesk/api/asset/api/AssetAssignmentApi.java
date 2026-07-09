package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.UUID;

/**
 * General asset-assignment queries across all employees (LLR-AST-02).
 * Base path: {@code /api/v1/asset-assignments}
 */
@Tag(name = "Asset Assignments", description = "General asset assignment queries with search and filter — LLR-AST-02")
@RequestMapping("/api/v1/asset-assignments")
@SecurityRequirement(name = "bearerAuth")
public interface AssetAssignmentApi {

    @Operation(
            summary = "List asset assignments",
            description = "Paginated list of asset assignments. Filter by employee, status, asset, " +
                          "and assignment date range. Requires assets:read permission."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('assets:read')")
    ResponseEntity<ApiResponse<PageResponse<AssetAssignmentDto>>> listAssignments(
            @Parameter(description = "Filter by employee UUID")
            @RequestParam(required = false) UUID employeeId,

            @Parameter(description = "Filter by assignment status (ACTIVE, RETURNED, LOST)")
            @RequestParam(required = false) AssignmentStatus status,

            @Parameter(description = "Filter by asset UUID")
            @RequestParam(required = false) UUID assetId,

            @Parameter(description = "Filter: assignment date on or after (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "Filter: assignment date on or before (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field (assignmentDate, createdAt)", example = "assignmentDate")
            @RequestParam(defaultValue = "assignmentDate") String sortBy,

            @Parameter(description = "Sort direction: ASC or DESC", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDir
    );
}
