package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.dto.AssetAttachmentDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST contract for Asset Attachments.
 * Base path: {@code /api/v1/assets/{assetId}/attachments}
 */
@Tag(name = "Asset Attachments", description = "Upload, list and delete asset attachments (manuals, invoices, warranty docs)")
@RequestMapping("/api/v1/assets/{assetId}/attachments")
@SecurityRequirement(name = "bearerAuth")
public interface AssetAttachmentApi {

    @Operation(summary = "Upload an attachment to an asset")
    @PostMapping
    @PreAuthorize("hasAuthority('assets:write') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<AssetAttachmentDto>> upload(
            @PathVariable UUID assetId,
            @RequestParam("file") MultipartFile file);

    @Operation(summary = "List all attachments for an asset")
    @GetMapping
    @PreAuthorize("hasAuthority('assets:read')")
    ResponseEntity<ApiResponse<List<AssetAttachmentDto>>> list(
            @PathVariable UUID assetId);

    @Operation(summary = "Download an attachment — streams the file bytes directly from object storage via the backend")
    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAuthority('assets:read') or hasAuthority('assets:manage')")
    void download(
            @PathVariable UUID assetId,
            @PathVariable UUID attachmentId,
            HttpServletResponse response) throws IOException;

    @Operation(summary = "Delete an attachment")
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('assets:write') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID assetId,
            @PathVariable UUID attachmentId);
}
