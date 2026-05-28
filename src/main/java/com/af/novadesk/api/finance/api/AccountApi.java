package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.AccountSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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

/**
 * REST API contract for Funding Account reference endpoints.
 *
 * <p>Base path: {@code /api/v1/finance/accounts}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in the standard {@link ApiResponse} envelope.</p>
 */
@Tag(name = "Funding Accounts", description = "Funding-account reference endpoints")
@RequestMapping("/api/v1/finance/accounts")
@SecurityRequirement(name = "bearerAuth")
public interface AccountApi {

    /**
     * GET /api/v1/finance/accounts
     * Lists all funding accounts.
     */
    @Operation(
            summary = "List funding accounts",
            description = "Retrieves all funding accounts with their associated legal entity details. "
                    + "Optionally filter by legal entity ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Accounts retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Records retrieved successfully",
                                      "data": [
                                        {
                                          "id": "00000000-0000-0000-0000-000000000001",
                                          "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                          "accountCode": "CASH-USD-001",
                                          "accountName": "Operating Cash Account",
                                          "accountRole": "CASH",
                                          "accountType": "ASSET",
                                          "currencyCode": "USD",
                                          "status": "ACTIVE",
                                          "createdAt": "2026-01-01T00:00:00"
                                        }
                                      ],
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> list(
            @Parameter(description = "Optional legal entity ID to filter accounts by entity")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            UUID legalEntityId
    );

    /**
     * GET /api/v1/finance/accounts/{id}
     * Returns a single funding account by UUID.
     */
    @Operation(
            summary = "Get account by ID",
            description = "Retrieves one funding account by its UUID, including associated legal entity details."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Record retrieved successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                        "accountCode": "CASH-USD-001",
                                        "accountName": "Operating Cash Account",
                                        "accountRole": "CASH",
                                        "accountType": "ASSET",
                                        "currencyCode": "USD",
                                        "status": "ACTIVE",
                                        "createdAt": "2026-01-01T00:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Account not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Account not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<AccountSummaryResponse>> getById(
            @Parameter(description = "Account UUID") @PathVariable UUID id
    );
}
