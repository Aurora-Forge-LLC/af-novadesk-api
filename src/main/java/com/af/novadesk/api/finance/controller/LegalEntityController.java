package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.finance.api.LegalEntityApi;
import com.af.novadesk.api.finance.dto.*;
import com.af.novadesk.api.finance.service.EntityUserAccessService;
import com.af.novadesk.api.finance.service.LegalEntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Multi-Entity Management (LLR-FIN-01).
 *
 * <p>Base path: {@code /api/v1/legal-entities}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Role-level authorisation
 * is enforced via {@code @PreAuthorize} using the permissions embedded in the JWT
 * and loaded into Spring Security's {@code GrantedAuthority} set by the
 * .</p>
 *
 * <p>Every response is wrapped in {@link ApiResponse} for a consistent envelope.</p>
 *
 * @see LegalEntityApi the API contract this controller implements
 */
@RestController
@RequiredArgsConstructor
public class LegalEntityController implements LegalEntityApi {

    private final LegalEntityService      legalEntityService;
    private final EntityUserAccessService accessService;

    // =========================================================================
    // LLR-FIN-01.1: Entity CRUD
    // =========================================================================

    /**
     * POST /api/v1/legal-entities
     * Creates a new legal entity in PENDING state.
     * Requires: organizations:write
     */
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> createEntity(
            @Valid @RequestBody LegalEntityDto request) {
        LegalEntityDto response = legalEntityService.createLegalEntity(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Legal entity created and pending approval"));
    }

    /**
     * GET /api/v1/legal-entities
     * Lists all entities within the caller's organization (paginated).
     * Requires: organizations:read
     */
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:read')")
    public ResponseEntity<ApiResponse<LegalEntityPageDto>> listEntities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "entityName") String sortBy) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        LegalEntityPageDto response = legalEntityService.listAll(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/legal-entities/{id}
     * Returns full detail for a single entity.
     * Requires: organizations:read
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:read')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> getEntity(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(legalEntityService.getById(id)));
    }

    /**
     * PATCH /api/v1/legal-entities/{id}/status
     * Activates or deactivates an entity.
     * Requires: organizations:write
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody LegalEntityDto request) {
        return ResponseEntity.ok(ApiResponse.ok(legalEntityService.updateStatus(id, request),
                "Entity status updated"));
    }

    // =========================================================================
    // LLR-FIN-01.2: Approval Lifecycle
    // =========================================================================

    /**
     * POST /api/v1/legal-entities/{id}/approve
     * Finance-team approves a pending entity, triggering CoA + bank account seeding.
     * Requires: organizations:write (admin/finance role enforced at RBAC level)
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> approveEntity(
            @PathVariable UUID id,
            @Valid @RequestBody ApproveEntityDto request) {
        LegalEntityDto response = legalEntityService.approveEntity(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Legal entity approved"));
    }

    /**
     * POST /api/v1/legal-entities/{id}/reject
     * Finance-team rejects a pending entity.
     * Requires: organizations:write
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> rejectEntity(
            @PathVariable UUID id,
            @Valid @RequestBody RejectEntityDto request) {
        LegalEntityDto response = legalEntityService.rejectEntity(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Legal entity rejected"));
    }

    // =========================================================================
    // LLR-FIN-01.3: User Access Management
    // =========================================================================

    /**
     * GET /api/v1/legal-entities/accessible
     * Returns entities the current user has active access to.
     * Drives the entity selector dropdown (LLR-FIN-01.3).
     * Requires: any authenticated user (no special permission)
     */
    @GetMapping("/accessible")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<LegalEntitySummaryDto>>> listAccessibleEntities() {
        return ResponseEntity.ok(ApiResponse.ok(accessService.listAccessibleEntities(),
                "Accessible entities retrieved"));
    }

    /**
     * GET /api/v1/legal-entities/{id}/access
     * Lists all user access grants for an entity (admin view).
     * Requires: users:read
     */
    @GetMapping("/{id}/access")
    @PreAuthorize("hasAuthority('users:read')")
    public ResponseEntity<ApiResponse<List<EntityUserAccessDto>>> listAccess(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accessService.listAccessForEntity(id)));
    }

    /**
     * POST /api/v1/legal-entities/{id}/access
     * Grants a user access to an entity with a given role.
     * Requires: users:write
     */
    @PostMapping("/{id}/access")
    @PreAuthorize("hasAuthority('users:write')")
    public ResponseEntity<ApiResponse<EntityUserAccessDto>> grantAccess(
            @PathVariable UUID id,
            @Valid @RequestBody EntityUserAccessDto request) {
        EntityUserAccessDto response = accessService.grantAccess(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response, "Access granted"));
    }

    /**
     * PATCH /api/v1/legal-entities/{entityId}/access/{accessId}/role
     * Updates the role of an existing access grant.
     * Requires: users:write
     */
    @PatchMapping("/{entityId}/access/{accessId}/role")
    @PreAuthorize("hasAuthority('users:write')")
    public ResponseEntity<ApiResponse<EntityUserAccessDto>> updateRole(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId,
            @Valid @RequestBody EntityUserAccessDto request) {
        return ResponseEntity.ok(ApiResponse.ok(
                accessService.updateRole(entityId, accessId, request), "Role updated"));
    }

    /**
     * DELETE /api/v1/legal-entities/{entityId}/access/{accessId}
     * Revokes a user's access to an entity.
     * Requires: users:write
     */
    @DeleteMapping("/{entityId}/access/{accessId}")
    @PreAuthorize("hasAuthority('users:write')")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId) {
        accessService.revokeAccess(entityId, accessId);
        return ResponseEntity.ok(ApiResponse.ok((Void) null, "Access revoked"));
    }

    /**
     * POST /api/v1/legal-entities/context/select
     * Records an entity context switch and returns the active context.
     * Requires: any authenticated user.
     * (LLR-FIN-01.3: persists across sessions, logged in audit trail)
     */
    @PostMapping("/context/select")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<EntityContextDto>> selectContext(
            @Valid @RequestBody EntityContextDto request) {
        EntityContextDto response = accessService.selectEntityContext(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Entity context selected"));
    }
}