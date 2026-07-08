package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;

/**
 * REST contract for Asset Lifecycle Management (LLR-AST-01).
 * Base path: {@code /api/v1/assets}
 */
@Tag(name = "Assets", description = "Asset registration, inventory, and lifecycle management (LLR-AST-01)")
@RequestMapping("/api/v1/assets")
@SecurityRequirement(name = "bearerAuth")
public interface AssetApi {

    @Operation(summary = "Register a new asset", description = "LLR-AST-01.1 — registers asset, generates QR code and depreciation schedule.")
    @PostMapping
    @PreAuthorize("hasAuthority('assets:write') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetDto>> register(@Valid @RequestBody AssetRegistrationRequest request);

    @Operation(summary = "Get asset by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetDto>> getById(@PathVariable UUID id);

    @Operation(summary = "List assets", description = "Paginated, filterable asset list.")
    @GetMapping
    @PreAuthorize("hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetPageDto>> list(
            @Parameter(description = "Filter by legal entity")              @RequestParam(required = false) UUID          legalEntityId,
            @Parameter(description = "Filter by asset status")              @RequestParam(required = false) AssetStatus   status,
            @Parameter(description = "Filter by asset category")            @RequestParam(required = false) AssetCategory category,
            @Parameter(description = "Search by asset type or serial #")    @RequestParam(required = false) String        q,
            @Parameter(description = "Filter by manufacturer")              @RequestParam(required = false) String        manufacturer,
            @Parameter(description = "Filter by current location")          @RequestParam(required = false) String        location,
            @Parameter(description = "Purchase date from (yyyy-MM-dd)")  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDateFrom,
            @Parameter(description = "Purchase date to (yyyy-MM-dd)")    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDateTo,
            @Parameter(description = "Warranty expiry from (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate warrantyExpiryFrom,
            @Parameter(description = "Warranty expiry to (yyyy-MM-dd)")   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate warrantyExpiryTo,
            @Parameter(description = "Page number (0-based)")              @RequestParam(defaultValue = "0")           int    page,
            @Parameter(description = "Page size")                          @RequestParam(defaultValue = "20")          int    size,
            @Parameter(description = "Sort field")                         @RequestParam(defaultValue = "createdAt")   String sortBy,
            @Parameter(description = "Sort direction: ASC or DESC")        @RequestParam(defaultValue = "DESC")        String sortDir);

    @Operation(summary = "Upload asset photo")
    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('assets:write') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetDto>> uploadPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file);

    @Operation(summary = "Get asset QR code URL")
    @GetMapping("/{id}/qr-code")
    @PreAuthorize("hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<String>> getQrCode(@PathVariable UUID id);

    @Operation(summary = "Download asset QR code label as PNG",
               description = "Streams the QR code PNG bytes through the API. Use this instead of the URL endpoint in environments where MinIO is not publicly reachable.")
    @GetMapping(value = "/{id}/qr-code/download", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<byte[]> downloadQrCode(@PathVariable UUID id);

    @Operation(summary = "Get depreciation schedule for an asset")
    @GetMapping("/{id}/depreciation")
    @PreAuthorize("hasAuthority('assets:depreciation:read') or hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<List<DepreciationScheduleDto>>> getDepreciationSchedule(@PathVariable UUID id);

    @Operation(summary = "Get custody history for an asset")
    @GetMapping("/{id}/custody-history")
    @PreAuthorize("hasAuthority('assets:read') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<List<CustodyTransferDto>>> getCustodyHistory(@PathVariable UUID id);

    @Operation(summary = "Assign asset to employee", description = "LLR-AST-02.1")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('assets:assign') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetAssignmentDto>> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAssignmentRequest request);

    @Operation(summary = "Reassign asset to a different employee", description = "LLR-AST-02.5")
    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAuthority('assets:assign') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetAssignmentDto>> reassign(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAssignmentRequest request);

    @Operation(summary = "Record asset return", description = "LLR-AST-03.3")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('assets:return') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<AssetDto>> recordReturn(
            @PathVariable UUID id,
            @Valid @RequestBody AssetReturnRequest request);

    @Operation(summary = "Request write-off for lost/unrecoverable asset", description = "LLR-AST-03.5")
    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasAuthority('assets:write') or hasAuthority('assets:manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<Void>> requestWriteOff(
            @PathVariable UUID id,
            @Valid @RequestBody WriteOffRequest request);
}
