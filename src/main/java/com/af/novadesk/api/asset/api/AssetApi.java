package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.dto.WriteOffSummaryDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

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
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AssetDto>> register(@Valid @RequestBody AssetRegistrationRequest request);

    @Operation(summary = "Get asset by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<AssetDto>> getById(@PathVariable UUID id);

    @Operation(summary = "List assets", description = "Filterable by legalEntityId, status, and category.")
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<AssetPageDto>> list(
            @RequestParam(required = false) UUID legalEntityId,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "Generate next serial number suggestion", description = "Returns an unused serial number suggestion for the given category (CATEGORY-YEAR-SEQ).")
    @GetMapping("/next-serial-number")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<String>> getNextSerialNumber(@RequestParam AssetCategory category);

    @Operation(summary = "Get manufacturer suggestions", description = "Returns manufacturers previously used by this organization for the given category, most-used first.")
    @GetMapping("/manufacturers")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<List<String>>> getManufacturerSuggestions(@RequestParam AssetCategory category);

    @Operation(summary = "Upload asset photo")
    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AssetDto>> uploadPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file);

    @Operation(summary = "Get asset QR code URL")
    @GetMapping("/{id}/qr-code")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<String>> getQrCode(@PathVariable UUID id);

    @Operation(summary = "Get depreciation schedule for an asset")
    @GetMapping("/{id}/depreciation")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<List<DepreciationScheduleDto>>> getDepreciationSchedule(@PathVariable UUID id);

    @Operation(summary = "Get custody history for an asset")
    @GetMapping("/{id}/custody-history")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<List<CustodyTransferDto>>> getCustodyHistory(@PathVariable UUID id);

    @Operation(summary = "Assign asset to employee", description = "LLR-AST-02.1")
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AssetAssignmentDto>> assign(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAssignmentRequest request);

    @Operation(summary = "Reassign asset to a different employee", description = "LLR-AST-02.5")
    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AssetAssignmentDto>> reassign(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAssignmentRequest request);

    @Operation(summary = "Record asset return", description = "LLR-AST-03.3")
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AssetDto>> recordReturn(
            @PathVariable UUID id,
            @Valid @RequestBody AssetReturnRequest request);

    @Operation(summary = "Request write-off for lost/unrecoverable asset", description = "LLR-AST-03.5")
    @PostMapping("/{id}/write-off")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<WriteOffSummaryDto>> requestWriteOff(
            @PathVariable UUID id,
            @Valid @RequestBody WriteOffRequest request);
}
