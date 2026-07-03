package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.dto.AssetDto;
import com.af.novadesk.api.asset.dto.WriteOffRequest;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Executive write-off approval endpoints (LLR-AST-03.5).
 * Base path: {@code /api/v1/assets/write-offs}
 */
@Tag(name = "Asset Write-offs", description = "Executive approval workflow for lost/unrecoverable assets (LLR-AST-03.5)")
@RequestMapping("/api/v1/assets/write-offs")
@SecurityRequirement(name = "bearerAuth")
public interface WriteOffApi {

    @Operation(summary = "Approve write-off with chosen action (WRITE_OFF / DEDUCT_FROM_PAY / REQUIRE_REIMBURSEMENT)")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('assets:approve-writeoff') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<AssetDto>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody WriteOffRequest request);

    @Operation(summary = "Reject write-off request")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('assets:approve-writeoff') or hasAuthority('assets:manage')")
    ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @RequestParam String reason);
}
