package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.CapitalInjectionDetailDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionPageDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.dto.CapitalInjectionStatusRequest;
import com.af.novadesk.api.finance.dto.InterEntityTransferDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * REST API contract for Decentralized Funding & Capital Injection (LLR-FIN-02).
 *
 * <p>Base path: {@code /api/v1/finance/funding}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in the standard {@link ApiResponse} envelope.</p>
 */
@Tag(name = "Capital Injection", description = "Decentralised funding and capital injection endpoints (LLR-FIN-02)")
@RequestMapping("/api/v1/finance/funding")
@SecurityRequirement(name = "bearerAuth")
public interface CapitalInjectionApi {

    // =========================================================================
    // LLR-FIN-02.1: Capital Injection Creation
    // =========================================================================

    /**
     * POST /api/v1/finance/funding/capital-injections
     * Records a new capital injection with double-entry ledger postings.
     */
    @Operation(
            summary = "Record a capital injection",
            description = "Creates a balanced double-entry posting for a funding event. " +
                    "For inter-entity transfers (INTER_ENTITY_TRANSFER), postings are written " +
                    "across both entity ledgers. " +
                    "USD conversion is automatic for non-USD entities (LLR-FIN-02.3)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Capital injection recorded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 201,
                                      "message": "Capital injection recorded successfully",
                                      "data": {
                                        "capitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
                                        "journalId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
                                        "transferId": null,
                                        "targetEntityCode": "INDIA",
                                        "sourceEntityCode": null,
                                        "amountLocal": 100000.0000,
                                        "currencyLocal": "INR",
                                        "amountUsd": 1200.0000,
                                        "exchangeRateUsed": 0.012000,
                                        "rateDateUsed": "2026-05-18",
                                        "rateSource": "API",
                                        "message": "Capital injection created and posted to ledger"
                                      },
                                      "timestamp": "2026-05-18T14:30:45.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or unbalanced entries",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "validation_error", value = """
                                    {
                                      "success": false,
                                      "code": 400,
                                      "message": "Validation failed. Please check the errors and try again",
                                      "data": null,
                                      "metadata": {
                                        "errors": [
                                          {"field": "amount", "message": "Amount must be at least 0.01", "code": "DecimalMin"}
                                        ]
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity or account not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Legal entity not found: INVALID",
                                      "data": null,
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "Exchange rate unavailable and manual rate not provided",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "rate_unavailable", value = """
                                    {
                                      "success": false,
                                      "code": 422,
                                      "message": "Exchange rate data is unavailable. Please provide a manual exchange rate",
                                      "data": null,
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/capital-injections")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<CapitalInjectionResponse>> createCapitalInjection(
            @Valid @RequestBody CapitalInjectionRequest request);

    // =========================================================================
    // LLR-FIN-02 Query: List Capital Injections (paginated)
    // =========================================================================

    /**
     * GET /api/v1/finance/funding/capital-injections
     * Lists capital injections for an entity, paginated.
     */
    @Operation(
            summary = "List capital injections",
            description = "Returns a paginated list of capital injections for the specified entity. " +
                    "Results are ordered by funding date descending. Requires VIEWER or higher access."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Capital injections retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Records retrieved successfully",
                                      "data": {
                                        "content": [
                                          {
                                            "id": "550e8400-e29b-41d4-a716-446655440000",
                                            "targetEntityCode": "INDIA",
                                            "targetEntityName": "India Operations",
                                            "sourceEntityCode": null,
                                            "fundingSource": "FOUNDER_EQUITY",
                                            "amountLocal": 100000.0000,
                                            "currencyLocal": "INR",
                                            "amountUsd": 1200.0000,
                                            "fundingDate": "2026-05-18",
                                            "exchangeRateUsed": 0.012000,
                                            "rateSource": "API",
                                            "injectionStatus": "POSTED",
                                            "referenceNumber": null,
                                            "createdBy": "finance.admin",
                                            "createdAt": "2026-05-18T14:30:45"
                                          }
                                        ],
                                        "page": 0,
                                        "size": 20,
                                        "totalElements": 1,
                                        "totalPages": 1
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/capital-injections")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<CapitalInjectionPageDto>> listCapitalInjections(
            @Parameter(description = "Legal entity code to scope the query", example = "INDIA")
            @RequestParam("entity_code") String entityCode,
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size);

    // =========================================================================
    // LLR-FIN-02 Query: Get Capital Injection Detail
    // =========================================================================

    /**
     * GET /api/v1/finance/funding/capital-injections/{id}
     * Returns full detail for a single capital injection, including ledger entries.
     */
    @Operation(
            summary = "Get capital injection detail",
            description = "Returns full detail for a single capital injection, including " +
                    "all associated ledger entries with account names and amounts."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Capital injection detail retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Record retrieved successfully",
                                      "data": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "journalId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
                                        "transferId": null,
                                        "targetEntityCode": "INDIA",
                                        "targetEntityName": "India Operations",
                                        "fundingSource": "FOUNDER_EQUITY",
                                        "fundingDate": "2026-05-18",
                                        "amountLocal": 100000.0000,
                                        "currencyLocal": "INR",
                                        "amountUsd": 1200.0000,
                                        "exchangeRateUsed": 0.012000,
                                        "rateDateUsed": "2026-05-18",
                                        "rateSource": "API",
                                        "sourceAccountId": "00000000-0000-0000-0000-000000000040",
                                        "sourceAccountName": "Founders - Equity",
                                        "destinationAccountId": "00000000-0000-0000-0000-000000000041",
                                        "destinationAccountName": "Bank - Operating",
                                        "referenceNumber": null,
                                        "notes": null,
                                        "injectionStatus": "POSTED",
                                        "createdBy": "finance.admin",
                                        "createdAt": "2026-05-18T14:30:45",
                                        "updatedAt": "2026-05-18T14:30:45",
                                        "ledgerEntries": [
                                          {
                                            "id": "00000000-0000-0000-0000-000000000050",
                                            "accountId": "00000000-0000-0000-0000-000000000040",
                                            "accountName": "Founders - Equity",
                                            "accountCode": "3001",
                                            "entrySide": "CREDIT",
                                            "amountLocal": 100000.0000,
                                            "amountUsd": 1200.0000,
                                            "description": "Capital injection - FOUNDER_EQUITY"
                                          },
                                          {
                                            "id": "00000000-0000-0000-0000-000000000051",
                                            "accountId": "00000000-0000-0000-0000-000000000041",
                                            "accountName": "Bank - Operating",
                                            "accountCode": "1001",
                                            "entrySide": "DEBIT",
                                            "amountLocal": 100000.0000,
                                            "amountUsd": 1200.0000,
                                            "description": "Capital injection - FOUNDER_EQUITY"
                                          }
                                        ]
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Capital injection not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Capital injection not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/capital-injections/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<CapitalInjectionDetailDto>> getCapitalInjection(
            @Parameter(description = "Capital injection UUID") @PathVariable UUID id);

    // =========================================================================
    // LLR-FIN-02 Lifecycle: Status change
    // =========================================================================

    /**
     * PATCH /api/v1/finance/funding/capital-injections/{id}/status
     * Updates the lifecycle status of a capital injection.
     */
    @Operation(
            summary = "Update capital injection status",
            description = "Updates the lifecycle status of a capital injection. " +
                    "Useful for flagging an injection for manual review (PENDING_REVIEW), " +
                    "recording a failure (FAILED), or voiding (VOID). " +
                    "Once voided, a compensating reversal entry should be created separately."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Capital injection status updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Capital injection status updated",
                                      "data": {
                                        "id": "550e8400-e29b-41d4-a716-446655440000",
                                        "injectionStatus": "PENDING_REVIEW"
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Capital injection not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Capital injection not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/capital-injections/{id}/status")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> updateCapitalInjectionStatus(
            @Parameter(description = "Capital injection UUID") @PathVariable UUID id,
            @Valid @RequestBody CapitalInjectionStatusRequest request);

    // =========================================================================
    // LLR-FIN-02.4: Inter-Entity Transfer Query
    // =========================================================================

    /**
     * GET /api/v1/finance/funding/inter-entity-transfers/{transferId}
     * Returns correlation details for an inter-entity transfer.
     */
    @Operation(
            summary = "Get inter-entity transfer details",
            description = "Returns the correlation details for an inter-entity transfer, " +
                    "linking the capital injection IDs and journal IDs across both entity ledgers " +
                    "for reconciliation purposes (LLR-FIN-02.4)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Inter-entity transfer details retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Records retrieved successfully",
                                      "data": {
                                        "transferId": "7c9d5e3f-2a1b-4c8d-9e0f-1a2b3c4d5e6f",
                                        "sourceEntityCode": "US",
                                        "targetEntityCode": "INDIA",
                                        "sourceCapitalInjectionId": "00000000-0000-0000-0000-000000000060",
                                        "targetCapitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
                                        "sourceJournalId": "00000000-0000-0000-0000-000000000061",
                                        "targetJournalId": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b"
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Transfer not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "No inter-entity transfer found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/inter-entity-transfers/{transferId}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<InterEntityTransferDto>> getInterEntityTransfer(
            @Parameter(description = "Transfer UUID") @PathVariable UUID transferId);
}
