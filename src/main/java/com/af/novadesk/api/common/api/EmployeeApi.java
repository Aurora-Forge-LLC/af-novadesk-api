package com.af.novadesk.api.common.api;

import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
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
        description = "Returns ACTIVE employees assigned to the specified legal entity. "
                    + "Used to populate the asset assignment employee dropdown."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<List<EmployeeDto>>> list(
            @RequestParam UUID legalEntityId);
}
