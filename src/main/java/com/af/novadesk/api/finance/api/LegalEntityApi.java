package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.dto.UpdateEntityStatusRequest;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Multi-Entity Management (LLR-FIN-01).
 *
 * <p>Base path: {@code /api/v1/legal-entities}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in the standard {@link ApiResponse} envelope.</p>
 */
@Tag(name = "Legal Entities", description = "Multi-entity management and administration endpoints")
@RequestMapping("/api/v1/legal-entities")
@SecurityRequirement(name = "bearerAuth")
public interface LegalEntityApi {

    // =========================================================================
    // LLR-FIN-01.1: Entity CRUD
    // =========================================================================

    /**
     * POST /api/v1/legal-entities
     * Creates a new legal entity in PENDING approval state.
     */
    @Operation(
            summary = "Create a legal entity",
            description = "Creates a new legal entity in PENDING approval state. " +
                    "The entity's base currency is auto-derived from the selected country. " +
                    "An outbox event (LEGAL_ENTITY_CREATED) is published for downstream notification."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Legal entity created and pending approval",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 201,
                                      "message": "Legal entity created and pending approval",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityCode": "TEST01",
                                        "country": "US",
                                        "baseCurrency": "USD",
                                        "taxId": "12-3456789",
                                        "incorporationDate": "2020-01-15",
                                        "approvalStatus": "PENDING",
                                        "status": "ACTIVE",
                                        "fiscalYearSetting": null,
                                        "chartOfAccounts": null,
                                        "bankAccounts": null,
                                        "createdAt": "2026-05-20T10:00:00",
                                        "updatedAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
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
                                          {"field": "entityName", "message": "Entity name is required", "code": "NotBlank"}
                                        ]
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Duplicate entity name or code",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "conflict", value = """
                                    {
                                      "success": false,
                                      "code": 409,
                                      "message": "Entity with name 'Test Entity' already exists",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LegalEntityDto>> createEntity(
            @Valid @RequestBody LegalEntityDto request);

    /**
     * GET /api/v1/legal-entities
     * Lists all entities within the caller's organization (paginated).
     */
    @Operation(
            summary = "List legal entities",
            description = "Returns a paginated list of all legal entities within the caller's organization. " +
                    "Results are ordered by the specified sort field (default: entityName)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entities retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Success",
                                      "data": {
                                        "content": [
                                          {
                                            "id": "00000000-0000-0000-0000-000000000010",
                                            "entityName": "Test Entity",
                                            "entityCode": "TEST01",
                                            "country": "US",
                                            "baseCurrency": "USD",
                                            "status": "ACTIVE",
                                            "approvalStatus": "PENDING"
                                          }
                                        ],
                                        "page": 0,
                                        "size": 20,
                                        "totalElements": 1,
                                        "totalPages": 1
                                      },
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
    ResponseEntity<ApiResponse<LegalEntityPageDto>> listEntities(
            @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "entityName") String sortBy);

    /**
     * GET /api/v1/legal-entities/{id}
     * Returns full detail for a single entity.
     */
    @Operation(
            summary = "Get legal entity by ID",
            description = "Returns the full detail for a single legal entity, including " +
                    "fiscal year settings, chart of accounts, and bank accounts when populated."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entity retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
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
    ResponseEntity<ApiResponse<LegalEntityDto>> getEntity(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id);

    /**
     * PATCH /api/v1/legal-entities/{id}/status
     * Activates or deactivates an entity.
     */
    @Operation(
            summary = "Update entity status",
            description = "Toggles the operational status of a legal entity between ACTIVE and INACTIVE. " +
                    "An outbox event (LEGAL_ENTITY_STATUS_CHANGED) is published for downstream notification."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entity status updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Entity status updated",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityCode": "TEST01",
                                        "country": "US",
                                        "baseCurrency": "USD",
                                        "approvalStatus": "APPROVED",
                                        "status": "INACTIVE",
                                        "createdAt": "2026-05-20T10:00:00",
                                        "updatedAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LegalEntityDto>> updateStatus(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateEntityStatusRequest request);

    // =========================================================================
    // LLR-FIN-01.2: Approval Lifecycle
    // =========================================================================

    /**
     * POST /api/v1/legal-entities/{id}/approve
     * Finance-team approves a pending entity, triggering CoA + bank account seeding.
     */
    @Operation(
            summary = "Approve a legal entity",
            description = "Transitions a PENDING entity to APPROVED status. Within the same transaction: " +
                    "seeds the Chart of Accounts from the country template, seeds default bank accounts, " +
                    "initialises FiscalYearSetting (with optional override), and publishes " +
                    "a LEGAL_ENTITY_APPROVED outbox event."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entity approved successfully; CoA, bank accounts, and fiscal year seeded",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Entity approved and seeded",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityCode": "TEST01",
                                        "country": "US",
                                        "baseCurrency": "USD",
                                        "approvalStatus": "APPROVED",
                                        "status": "ACTIVE",
                                        "fiscalYearSetting": {
                                          "id": "00000000-0000-0000-0000-000000000030",
                                          "fiscalStartMonth": 1,
                                          "fiscalStartDay": 1,
                                          "fiscalEndMonth": 12,
                                          "fiscalEndDay": 31,
                                          "currentFiscalYear": 2026,
                                          "periodsPerYear": 12
                                        },
                                        "chartOfAccounts": [],
                                        "bankAccounts": [],
                                        "createdAt": "2026-05-20T10:00:00",
                                        "updatedAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Entity is not in PENDING state",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "invalid_state", value = """
                                    {
                                      "success": false,
                                      "code": 400,
                                      "message": "Entity is not in PENDING state",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LegalEntityDto>> approveEntity(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id,
            @Valid @RequestBody ApproveEntityDto request);

    /**
     * POST /api/v1/legal-entities/{id}/reject
     * Finance-team rejects a pending entity.
     */
    @Operation(
            summary = "Reject a legal entity",
            description = "Transitions a PENDING entity to REJECTED status with a mandatory rejection reason. " +
                    "Publishes a LEGAL_ENTITY_REJECTED outbox event for downstream notification."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entity rejected successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Entity rejected",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityCode": "TEST01",
                                        "country": "US",
                                        "baseCurrency": "USD",
                                        "approvalStatus": "REJECTED",
                                        "status": "ACTIVE",
                                        "createdAt": "2026-05-20T10:00:00",
                                        "updatedAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Entity is not in PENDING state",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "invalid_state", value = """
                                    {
                                      "success": false,
                                      "code": 400,
                                      "message": "Entity is not in PENDING state",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LegalEntityDto>> rejectEntity(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id,
            @Valid @RequestBody RejectEntityDto request);

    // =========================================================================
    // LLR-FIN-01.3: User Access Management
    // =========================================================================

    /**
     * GET /api/v1/legal-entities/accessible
     * Returns entities the current user has active access to.
     */
    @Operation(
            summary = "List accessible entities",
            description = "Returns the legal entities the current authenticated user has active access to. " +
                    "Drives the entity selector dropdown in the UI (LLR-FIN-01.3). " +
                    "Accessible by any authenticated user."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Accessible entities retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Accessible entities retrieved",
                                      "data": [
                                        {
                                          "id": "00000000-0000-0000-0000-000000000010",
                                          "entityName": "Test Entity",
                                          "entityCode": "TEST01",
                                          "country": "US",
                                          "baseCurrency": "USD",
                                          "status": "ACTIVE",
                                          "approvalStatus": "APPROVED"
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
    @GetMapping("/accessible")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<LegalEntitySummaryDto>>> listAccessibleEntities();

    /**
     * GET /api/v1/legal-entities/{id}/access
     * Lists all user access grants for an entity (admin view).
     */
    @Operation(
            summary = "List access grants",
            description = "Lists all user access grants for a given legal entity (admin view). " +
                    "Includes user details such as email, display name, assigned role, and last accessed timestamp."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Access grants retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Success",
                                      "data": [
                                        {
                                          "id": "00000000-0000-0000-0000-000000000020",
                                          "authUserId": "00000000-0000-0000-0000-000000000002",
                                          "email": "john.doe@example.com",
                                          "displayName": "John Doe",
                                          "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                          "entityName": "Test Entity",
                                          "entityRole": "VIEWER",
                                          "status": "ACTIVE",
                                          "lastAccessedAt": null,
                                          "createdAt": "2026-05-20T10:00:00"
                                        }
                                      ],
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}/access")
    @PreAuthorize("hasAuthority('users:read')")
    ResponseEntity<ApiResponse<List<EntityUserAccessDto>>> listAccess(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id);

    /**
     * POST /api/v1/legal-entities/{id}/access
     * Grants a user access to an entity with a given role.
     */
    @Operation(
            summary = "Grant entity access",
            description = "Grants a user access to a legal entity with a specified role. " +
                    "Validates that the user exists and that no duplicate access grant already exists. " +
                    "Returns 201 Created on success."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Access granted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 201,
                                      "message": "Access granted",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000020",
                                        "authUserId": "00000000-0000-0000-0000-000000000002",
                                        "email": "john.doe@example.com",
                                        "displayName": "John Doe",
                                        "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityRole": "VIEWER",
                                        "status": "ACTIVE",
                                        "lastAccessedAt": null,
                                        "createdAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity or shadow user not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity or user not found",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Access already exists for this user and entity",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "conflict", value = """
                                    {
                                      "success": false,
                                      "code": 409,
                                      "message": "User already has access to this entity",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/{id}/access")
    @PreAuthorize("hasAuthority('users:write')")
    ResponseEntity<ApiResponse<EntityUserAccessDto>> grantAccess(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID id,
            @Valid @RequestBody EntityUserAccessDto request);

    /**
     * PATCH /api/v1/legal-entities/{entityId}/access/{accessId}/role
     * Updates the role of an existing access grant.
     */
    @Operation(
            summary = "Update access role",
            description = "Updates the role of an existing user access grant for a legal entity. " +
                    "Role must be one of: VIEWER, EDITOR, APPROVER, ADMIN."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Access role updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Access role updated",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000020",
                                        "authUserId": "00000000-0000-0000-0000-000000000002",
                                        "email": "john.doe@example.com",
                                        "displayName": "John Doe",
                                        "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityRole": "ADMIN",
                                        "status": "ACTIVE",
                                        "lastAccessedAt": null,
                                        "createdAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Access grant not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Access grant not found",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{entityId}/access/{accessId}/role")
    @PreAuthorize("hasAuthority('users:write')")
    ResponseEntity<ApiResponse<EntityUserAccessDto>> updateRole(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID entityId,
            @Parameter(description = "Access grant UUID") @PathVariable UUID accessId,
            @Valid @RequestBody EntityUserAccessDto request);

    /**
     * DELETE /api/v1/legal-entities/{entityId}/access/{accessId}
     * Revokes a user's access to an entity.
     */
    @Operation(
            summary = "Revoke entity access",
            description = "Revokes a user's access to a legal entity (soft-delete by setting status to INACTIVE). " +
                    "Returns a void (null data) success response on completion."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Access revoked successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Access revoked",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Access grant not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Access grant not found",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{entityId}/access/{accessId}")
    @PreAuthorize("hasAuthority('users:write')")
    ResponseEntity<ApiResponse<Void>> revokeAccess(
            @Parameter(description = "Legal entity UUID") @PathVariable UUID entityId,
            @Parameter(description = "Access grant UUID") @PathVariable UUID accessId);

    /**
     * POST /api/v1/legal-entities/context/select
     * Records an entity context switch and returns the active context.
     */
    @Operation(
            summary = "Select entity context",
            description = "Records an entity context switch for the current user and returns the active context. " +
                    "Validates the user has active access to the entity. " +
                    "The selected context is persisted across sessions and logged in the audit trail (LLR-FIN-01.3)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Entity context selected successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Entity context selected",
                                      "data": {
                                        "legalEntityId": "00000000-0000-0000-0000-000000000010",
                                        "entityName": "Test Entity",
                                        "entityCode": "TEST01",
                                        "baseCurrency": "USD",
                                        "selectedAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "User does not have active access to this entity",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "forbidden", value = """
                                    {
                                      "success": false,
                                      "code": 403,
                                      "message": "Access denied",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Entity not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Entity not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/context/select")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<EntityContextDto>> selectContext(
            @Valid @RequestBody EntityContextDto request);
}
