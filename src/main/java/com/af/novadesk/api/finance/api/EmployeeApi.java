package com.af.novadesk.api.common.api;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * REST contract for common employee identity queries.
 * Base path: {@code /api/v1/employees}
 *
 * <p>Used by asset assignment dropdown and other cross-module employee lookups.</p>
 */
@Tag(name = "Employees", description = "Common employee identity queries (shared across modules)")
@RequestMapping("/api/v1/employees")
@SecurityRequirement(name = "bearerAuth")
public interface EmployeeApi {

    @Operation(
        summary     = "List employees",
        description = "Returns employees with optional search and filter. "
                    + "All params are optional; omitting them returns all org employees (paginated)."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('employees:read')")
    ResponseEntity<ApiResponse<PageResponse<EmployeeDto>>> list(
            @Parameter(description = "Search by name, email, or employee code") @RequestParam(required = false) String         q,
            @Parameter(description = "Filter by employee status")               @RequestParam(required = false) EmployeeStatus status,
            @Parameter(description = "Filter by legal entity assignment")       @RequestParam(required = false) UUID           legalEntityId,
            @Parameter(description = "Filter by direct manager ID")             @RequestParam(required = false) UUID           managerId,
            @Parameter(description = "Zero-based page index")  @RequestParam(defaultValue = "0")           int    page,
            @Parameter(description = "Page size")              @RequestParam(defaultValue = "20")          int    size,
            @Parameter(description = "Sort field")             @RequestParam(defaultValue = "displayName") String sortBy,
            @Parameter(description = "Sort direction: ASC or DESC") @RequestParam(defaultValue = "ASC")   String sortDir);
}
