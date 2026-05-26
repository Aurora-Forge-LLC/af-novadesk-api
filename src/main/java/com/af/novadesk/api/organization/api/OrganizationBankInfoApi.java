package com.af.novadesk.api.organization.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.organization.dto.BankAccountBalanceResponse;
import com.af.novadesk.api.organization.dto.OrganizationBankCreditRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Organization Bank Info endpoints.
 *
 * <p>Base path: {@code /api/v1/organization/bank-info}</p>
 *
 * <p>All endpoints require a valid JWT bearer token with {@code Super_admin}
 * authority. Every response is wrapped in the standard {@link ApiResponse}
 * envelope.</p>
 */
@Tag(name = "Organization Bank Info", description = "Organization-level bank account management (Super_admin only)")
@RequestMapping("/api/v1/organization/bank-info")
@SecurityRequirement(name = "bearerAuth")
public interface OrganizationBankInfoApi {

    /**
     * POST /api/v1/organization/bank-info
     * Create a new bank account record for the organization.
     */
    @Operation(
            summary = "Create organization bank info",
            description = "Creates a new bank account record for the authenticated user's organization. " +
                    "The orgId and userId are automatically populated from the JWT. " +
                    "Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Bank info created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 201,
                                      "message": "Record created successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "orgId": "00000000-0000-0000-0000-000000000010",
                                        "userId": "00000000-0000-0000-0000-000000000020",
                                        "bankName": "Chase Bank",
                                        "accountHolderName": "Aurora Forge LLC",
                                        "accountNumber": "****1234",
                                        "iban": "US12345678901234567890123456",
                                        "swiftCode": "CHASUS33",
                                        "bankAddress": "123 Main St, New York, NY 10001",
                                        "currency": "USD",
                                        "balance": 0.0000,
                                        "primary": true,
                                        "status": "ACTIVE",
                                        "createdAt": "2026-05-26T10:00:00",
                                        "updatedAt": "2026-05-26T10:00:00"
                                      },
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate account number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> create(
            @Parameter(description = "Bank account details") @Valid @RequestBody OrganizationBankInfoRequest request
    );

    /**
     * GET /api/v1/organization/bank-info/{id}
     * Retrieve a single bank account record by ID.
     */
    @Operation(
            summary = "Get bank info by ID",
            description = "Retrieves a single organization bank account record by its UUID. " +
                    "The response includes the current available balance. " +
                    "Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Bank info retrieved successfully",
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
                                        "orgId": "00000000-0000-0000-0000-000000000010",
                                        "userId": "00000000-0000-0000-0000-000000000020",
                                        "bankName": "Chase Bank",
                                        "accountHolderName": "Aurora Forge LLC",
                                        "accountNumber": "****1234",
                                        "iban": "US12345678901234567890123456",
                                        "swiftCode": "CHASUS33",
                                        "bankAddress": "123 Main St, New York, NY 10001",
                                        "currency": "USD",
                                        "balance": 15000.0000,
                                        "primary": true,
                                        "status": "ACTIVE",
                                        "createdAt": "2026-05-26T10:00:00",
                                        "updatedAt": "2026-05-26T10:00:00"
                                      },
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bank info not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> getById(
            @Parameter(description = "Bank info record UUID") @PathVariable UUID id
    );

    /**
     * GET /api/v1/organization/bank-info
     * List all bank account records for the organization.
     */
    @Operation(
            summary = "List organization bank info",
            description = "Retrieves all bank account records for the authenticated user's organization. " +
                    "Each record includes the current available balance. " +
                    "Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Bank info records retrieved successfully",
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
                                          "orgId": "00000000-0000-0000-0000-000000000010",
                                          "userId": "00000000-0000-0000-0000-000000000020",
                                          "bankName": "Chase Bank",
                                          "accountHolderName": "Aurora Forge LLC",
                                          "accountNumber": "****1234",
                                          "iban": "US12345678901234567890123456",
                                          "swiftCode": "CHASUS33",
                                          "bankAddress": "123 Main St, New York, NY 10001",
                                          "currency": "USD",
                                          "balance": 15000.0000,
                                          "primary": true,
                                          "status": "ACTIVE",
                                          "createdAt": "2026-05-26T10:00:00",
                                          "updatedAt": "2026-05-26T10:00:00"
                                        }
                                      ],
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<List<OrganizationBankInfoResponse>>> list();

    /**
     * PUT /api/v1/organization/bank-info/{id}
     * Update an existing bank account record.
     */
    @Operation(
            summary = "Update organization bank info",
            description = "Updates an existing bank account record for the organization. " +
                    "Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Bank info updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Record updated successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "orgId": "00000000-0000-0000-0000-000000000010",
                                        "bankName": "Chase Bank",
                                        "accountHolderName": "Aurora Forge LLC",
                                        "accountNumber": "****5678",
                                        "currency": "USD",
                                        "balance": 15000.0000,
                                        "primary": true,
                                        "status": "ACTIVE"
                                      },
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bank info not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate account number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> update(
            @Parameter(description = "Bank info record UUID") @PathVariable UUID id,
            @Parameter(description = "Updated bank account details") @Valid @RequestBody OrganizationBankInfoRequest request
    );

    /**
     * DELETE /api/v1/organization/bank-info/{id}
     * Soft-delete a bank account record.
     */
    @Operation(
            summary = "Delete organization bank info",
            description = "Soft-deletes (marks as DELETED) a bank account record for the organization. " +
                    "Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Bank info deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Record deleted successfully",
                                      "data": null,
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bank info not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Bank info record UUID") @PathVariable UUID id
    );

    // ── Balance Operations ─────────────────────────────────────────────────

    /**
     * PUT /api/v1/organization/bank-info/{id}/credit
     * Credit (deposit) funds into a bank account.
     */
    @Operation(
            summary = "Credit organization bank account",
            description = "Deposits funds into the specified organization bank account, increasing its balance. " +
                    "Use this endpoint to load money into the account before making capital injections or " +
                    "entity transfers. Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account credited successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Account credited successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "balance": 25000.0000
                                      },
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (amount must be positive)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bank info not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PutMapping("/{id}/credit")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<BankAccountBalanceResponse>> credit(
            @Parameter(description = "Bank info record UUID") @PathVariable UUID id,
            @Parameter(description = "Amount to credit") @Valid @RequestBody OrganizationBankCreditRequest request
    );

    /**
     * PUT /api/v1/organization/bank-info/{id}/debit
     * Debit (withdraw) funds from a bank account.
     */
    @Operation(
            summary = "Debit organization bank account",
            description = "Withdraws funds from the specified organization bank account, decreasing its balance. " +
                    "Use this endpoint when transferring money out to fund capital injections or " +
                    "entity transfers. Requires sufficient balance. Requires Super_admin authority."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account debited successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Account debited successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "balance": 5000.0000
                                      },
                                      "timestamp": "2026-05-26T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (amount must be positive)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions (Super_admin required)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bank info not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Insufficient funds"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PutMapping("/{id}/debit")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    ResponseEntity<ApiResponse<BankAccountBalanceResponse>> debit(
            @Parameter(description = "Bank info record UUID") @PathVariable UUID id,
            @Parameter(description = "Amount to debit") @Valid @RequestBody OrganizationBankCreditRequest request
    );
}
