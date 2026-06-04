package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.dto.AssetAssignmentDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public acknowledgment endpoint — no authentication required.
 * Employee clicks the link in their assignment email and hits this endpoint.
 * Base path: {@code /api/v1/assets/acknowledge}
 */
@Tag(name = "Asset Acknowledgment", description = "Public endpoint for employee asset acknowledgment (LLR-AST-02.2)")
@RequestMapping("/api/v1/assets/acknowledge")
public interface AcknowledgmentApi {

    @Operation(summary = "Acknowledge asset receipt via one-time token",
               description = "LLR-AST-02.2 — employee clicks email link; token is validated and acknowledgment recorded.")
    @PostMapping("/{token}")
    ResponseEntity<ApiResponse<AssetAssignmentDto>> acknowledge(
            @PathVariable String token,
            HttpServletRequest request);
}
