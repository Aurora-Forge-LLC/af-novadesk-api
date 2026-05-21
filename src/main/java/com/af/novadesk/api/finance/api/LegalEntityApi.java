package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.finance.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Multi-Entity Management (LLR-FIN-01).
 *
 * <p>Base path: {@code /api/v1/legal-entities}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in {@link ApiResponse} for a consistent envelope.</p>
 */
@RequestMapping("/api/v1/legal-entities")
public interface LegalEntityApi {

    // =========================================================================
    // LLR-FIN-01.1: Entity CRUD
    // =========================================================================

    /**
     * POST /api/v1/legal-entities
     * Creates a new legal entity in PENDING state.
     */
    @PostMapping
    ResponseEntity<ApiResponse<LegalEntityDto>> createEntity(
            @Valid @RequestBody LegalEntityDto request);

    /**
     * GET /api/v1/legal-entities
     * Lists all entities within the caller's organization (paginated).
     */
    @GetMapping
    ResponseEntity<ApiResponse<LegalEntityPageDto>> listEntities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "entityName") String sortBy);

    /**
     * GET /api/v1/legal-entities/{id}
     * Returns full detail for a single entity.
     */
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<LegalEntityDto>> getEntity(@PathVariable UUID id);

    /**
     * PATCH /api/v1/legal-entities/{id}/status
     * Activates or deactivates an entity.
     */
    @PatchMapping("/{id}/status")
    ResponseEntity<ApiResponse<LegalEntityDto>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LegalEntityDto request);

    // =========================================================================
    // LLR-FIN-01.2: Approval Lifecycle
    // =========================================================================

    /**
     * POST /api/v1/legal-entities/{id}/approve
     * Finance-team approves a pending entity, triggering CoA + bank account seeding.
     */
    @PostMapping("/{id}/approve")
    ResponseEntity<ApiResponse<LegalEntityDto>> approveEntity(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveEntityDto request);

    /**
     * POST /api/v1/legal-entities/{id}/reject
     * Finance-team rejects a pending entity.
     */
    @PostMapping("/{id}/reject")
    ResponseEntity<ApiResponse<LegalEntityDto>> rejectEntity(
            @PathVariable UUID id,
            @Valid @RequestBody RejectEntityDto request);

    // =========================================================================
    // LLR-FIN-01.3: User Access Management
    // =========================================================================

    /**
     * GET /api/v1/legal-entities/accessible
     * Returns entities the current user has active access to.
     * Drives the entity selector dropdown (LLR-FIN-01.3).
     */
    @GetMapping("/accessible")
    ResponseEntity<ApiResponse<List<LegalEntitySummaryDto>>> listAccessibleEntities();

    /**
     * GET /api/v1/legal-entities/{id}/access
     * Lists all user access grants for an entity (admin view).
     */
    @GetMapping("/{id}/access")
    ResponseEntity<ApiResponse<List<EntityUserAccessDto>>> listAccess(@PathVariable UUID id);

    /**
     * POST /api/v1/legal-entities/{id}/access
     * Grants a user access to an entity with a given role.
     */
    @PostMapping("/{id}/access")
    ResponseEntity<ApiResponse<EntityUserAccessDto>> grantAccess(
            @PathVariable UUID id,
            @Valid @RequestBody EntityUserAccessDto request);

    /**
     * PATCH /api/v1/legal-entities/{entityId}/access/{accessId}/role
     * Updates the role of an existing access grant.
     */
    @PatchMapping("/{entityId}/access/{accessId}/role")
    ResponseEntity<ApiResponse<EntityUserAccessDto>> updateRole(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId,
            @Valid @RequestBody EntityUserAccessDto request);

    /**
     * DELETE /api/v1/legal-entities/{entityId}/access/{accessId}
     * Revokes a user's access to an entity.
     */
    @DeleteMapping("/{entityId}/access/{accessId}")
    ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId);

    /**
     * POST /api/v1/legal-entities/context/select
     * Records an entity context switch and returns the active context.
     * (LLR-FIN-01.3: persists across sessions, logged in audit trail)
     */
    @PostMapping("/context/select")
    ResponseEntity<ApiResponse<EntityContextDto>> selectContext(
            @Valid @RequestBody EntityContextDto request);
}
