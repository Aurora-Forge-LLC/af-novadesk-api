package com.af.novadesk.api.finance.funding.api;

import com.af.novadesk.api.finance.funding.dto.AccountSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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

@Tag(name = "Funding Accounts", description = "Funding-account reference endpoints")
@RequestMapping("/api/v1/finance/accounts")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public interface AccountApi {

    @Operation(summary = "List funding accounts", description = "Retrieve all funding accounts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.af.novadesk.api.common.response.ApiResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<List<AccountSummaryResponse>>> list();

    @Operation(summary = "Get account by ID", description = "Retrieve one funding account by UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<AccountSummaryResponse>> getById(
            @Parameter(description = "Account UUID") @PathVariable UUID id
    );
}

