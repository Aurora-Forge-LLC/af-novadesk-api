package com.af.novadesk.api.expense.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.expense.dto.VendorDto;
import com.af.novadesk.api.expense.dto.VendorPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API contract for Vendor Management (LLR-FIN-03.3).
 *
 * <p>Base path: {@code /api/v1/expense/vendors}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in {@link ApiResponse} for a consistent envelope.</p>
 */
@Tag(name = "Vendors", description = "Vendor (payee) registry management — LLR-FIN-03.3")
@RequestMapping("/api/v1/expense/vendors")
@SecurityRequirement(name = "bearerAuth")
public interface VendorApi {

    // =========================================================================
    // LLR-FIN-03.3: Vendor CRUD
    // =========================================================================

    /**
     * POST /api/v1/expense/vendors
     * Creates a new vendor in the organization's registry.
     */
    @Operation(
            summary     = "Create vendor",
            description = "Registers a new vendor (payee) in the organization's vendor list. " +
                          "Optionally sets a default expense account for form auto-fill (LLR-FIN-03.3)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vendor created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Vendor name already exists in this organization"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('VENDOR_CREATE')")
    ResponseEntity<ApiResponse<VendorDto>> createVendor(
            @Valid @RequestBody VendorDto request);

    /**
     * GET /api/v1/expense/vendors
     * Lists all vendors in the caller's organization (paginated).
     * Supports optional name search for autocomplete on the expense form.
     */
    @Operation(
            summary     = "List vendors",
            description = "Returns a paginated list of all vendors in the caller's organization. " +
                          "Pass `search` to filter by name for autocomplete support (LLR-FIN-03.1)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendors retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('VENDOR_READ')")
    ResponseEntity<ApiResponse<VendorPageDto>> listVendors(
            @Parameter(description = "Page number (0-based)")  @RequestParam(defaultValue = "0")          int    page,
            @Parameter(description = "Page size")              @RequestParam(defaultValue = "20")         int    size,
            @Parameter(description = "Sort field")             @RequestParam(defaultValue = "vendorName") String sortBy,
            @Parameter(description = "Name fragment for autocomplete search (optional)")
                                                               @RequestParam(required = false)            String search);

    /**
     * GET /api/v1/expense/vendors/{id}
     * Returns full detail for a single vendor.
     */
    @Operation(
            summary     = "Get vendor by ID",
            description = "Retrieves a single vendor by UUID. Scoped to the caller's organization."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendor retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENDOR_READ')")
    ResponseEntity<ApiResponse<VendorDto>> getVendor(
            @Parameter(description = "Vendor UUID") @PathVariable UUID id);

    /**
     * PATCH /api/v1/expense/vendors/{id}
     * Updates an existing vendor's details.
     */
    @Operation(
            summary     = "Update vendor",
            description = "Updates vendor name, type, tax ID, or default account. " +
                          "Scoped to the caller's organization."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Vendor updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Vendor name already taken"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('VENDOR_UPDATE')")
    ResponseEntity<ApiResponse<VendorDto>> updateVendor(
            @Parameter(description = "Vendor UUID") @PathVariable UUID id,
            @Valid @RequestBody VendorDto request);

    /**
     * PATCH /api/v1/expense/vendors/{id}/status
     * Activates or deactivates a vendor.
     * Inactive vendors are hidden from the expense form autocomplete.
     */
    @Operation(
            summary     = "Update vendor status",
            description = "Activates (ACTIVE) or deactivates (INACTIVE) a vendor. " +
                          "Inactive vendors are excluded from the expense form autocomplete."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('VENDOR_UPDATE')")
    ResponseEntity<ApiResponse<VendorDto>> updateVendorStatus(
            @Parameter(description = "Vendor UUID") @PathVariable UUID id,
            @Valid @RequestBody VendorDto request);
}
