package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Chart of Accounts reference endpoints.
 *
 * <p>Base path: {@code /api/v1/finance/chart-of-accounts}</p>
 *
 * <p>Used by the frontend to populate the expense category dropdown.
 * Filter by {@code accountType=EXPENSE} to show only expense categories.</p>
 */
@Tag(name = "Chart of Accounts", description = "Chart of Accounts reference endpoints (LLR-FIN-01.2)")
@RequestMapping("/api/v1/finance/chart-of-accounts")
@SecurityRequirement(name = "bearerAuth")
public interface ChartOfAccountApi {

    /**
     * GET /api/v1/finance/chart-of-accounts?legalEntityId={id}
     * GET /api/v1/finance/chart-of-accounts?legalEntityId={id}&accountType=EXPENSE
     *
     * Lists all Chart of Accounts entries for a legal entity, optionally filtered by type.
     */
    @Operation(
            summary = "List chart of accounts",
            description = "Returns all Chart of Accounts entries for the specified legal entity, "
                    + "ordered by account code. Optionally filter by accountType "
                    + "(e.g. EXPENSE to populate the expense category dropdown)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Chart of accounts retrieved successfully",
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
                                          "accountCode": "5000",
                                          "accountName": "Cost of Sales",
                                          "accountType": "EXPENSE",
                                          "description": null,
                                          "parentAccountId": null,
                                          "postable": true,
                                          "systemGenerated": true,
                                          "status": "ACTIVE"
                                        },
                                        {
                                          "id": "00000000-0000-0000-0000-000000000002",
                                          "accountCode": "5100",
                                          "accountName": "Administrative Expenses",
                                          "accountType": "EXPENSE",
                                          "description": null,
                                          "parentAccountId": null,
                                          "postable": true,
                                          "systemGenerated": true,
                                          "status": "ACTIVE"
                                        }
                                      ],
                                      "timestamp": "2026-06-01T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Missing or invalid legalEntityId"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Not authenticated"
            )
    })
    @GetMapping
    @PreAuthorize("hasAuthority('financial:read')")
    ResponseEntity<ApiResponse<List<ChartOfAccountDto>>> list(
            @Parameter(description = "Legal entity UUID — required", required = true)
            @RequestParam UUID legalEntityId,

            @Parameter(description = "Optional account type filter. Use EXPENSE for the expense category dropdown.",
                       example = "EXPENSE")
            @RequestParam(required = false) AccountType accountType
    );
}
