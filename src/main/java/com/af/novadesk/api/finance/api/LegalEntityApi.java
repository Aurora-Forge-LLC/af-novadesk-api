package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.finance.dto.LegalEntitySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@Tag(name = "Legal Entities", description = "Legal-entity reference endpoints for finance modules")
@RequestMapping("/api/v1/finance/legal-entities")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public interface LegalEntityApi {

    @Operation(summary = "List legal entities", description = "Retrieve all legal entities used by finance workflows")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entities retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.af.novadesk.api.common.response.ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Records retrieved successfully",
                                      "data": [
                                        {"id":"3fa85f64-5717-4562-b3fc-2c963f66afa6","entity_code":"INDIA","base_currency":"INR"}
                                      ],
                                      "timestamp": "2026-05-18T10:00:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<List<LegalEntitySummaryResponse>>> list();

    @Operation(summary = "Get legal entity by ID", description = "Retrieve one legal entity by UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Legal entity retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Legal entity not found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<LegalEntitySummaryResponse>> getById(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id
    );
}

