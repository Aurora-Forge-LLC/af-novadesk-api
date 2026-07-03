package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.LegalEntityApi;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.dto.UpdateEntityStatusRequest;
import com.af.novadesk.api.finance.service.EntityUserAccessService;
import com.af.novadesk.api.finance.service.LegalEntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                .body(ApiResponse.success(201, "Legal entity created and pending approval", response));
    }

    /**
     * GET /api/v1/legal-entities
     * Lists all entities within the caller's organization (paginated).
     * Requires: organizations:write
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('organizations:read','organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityPageDto>> listEntities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "entityName") String sortBy) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());
        LegalEntityPageDto response = legalEntityService.listAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(200, "Success", response));
    }

    /**
     * GET /api/v1/legal-entities/{id}
     * Returns full detail for a single entity.
     * Requires: organizations:write
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('organizations:read','organizations:write')")
    public ResponseEntity<ApiResponse<LegalEntityDto>> getEntity(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(200, "Entity retrieved successfully", legalEntityService.getById(id)));
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
            @Valid @RequestBody UpdateEntityStatusRequest request) {
        LegalEntityDto data = legalEntityService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Entity status updated", data));
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
        return ResponseEntity.ok(ApiResponse.success(200, "Legal entity approved", response));
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
        return ResponseEntity.ok(ApiResponse.success(200, "Legal entity rejected", response));
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
        List<LegalEntitySummaryDto> data = accessService.listAccessibleEntities();
        return ResponseEntity.ok(ApiResponse.success(200, "Accessible entities retrieved", data));
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
        return ResponseEntity.ok(ApiResponse.success(200, "Success", accessService.listAccessForEntity(id)));
    }

    /**
     * POST /api/v1/legal-entities/{id}/access
     * Grants a user access to an entity with a given role.
     * Requires: users:write
     */
    @PostMapping("/{id}/access")
    @PreAuthorize("hasRole('ENTITY_ADMIN') or hasRole('ORG_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EntityUserAccessDto>> grantAccess(
            @PathVariable UUID id,
            @Valid @RequestBody EntityUserAccessDto request) {
        EntityUserAccessDto response = accessService.grantAccess(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Access granted", response));
    }

    /**
     * PATCH /api/v1/legal-entities/{entityId}/access/{accessId}/role
     * Updates the role of an existing access grant.
     * Requires: users:write
     */
    @PatchMapping("/{entityId}/access/{accessId}/role")
    @PreAuthorize("hasRole('ENTITY_ADMIN') or hasRole('ORG_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<EntityUserAccessDto>> updateRole(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId,
            @Valid @RequestBody EntityUserAccessDto request) {
        EntityUserAccessDto data = accessService.updateRole(entityId, accessId, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Role updated", data));
    }

    /**
     * DELETE /api/v1/legal-entities/{entityId}/access/{accessId}
     * Revokes a user's access to an entity.
     * Requires: users:write
     */
    @DeleteMapping("/{entityId}/access/{accessId}")
    @PreAuthorize("hasRole('ENTITY_ADMIN') or hasRole('ORG_ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable UUID entityId,
            @PathVariable UUID accessId) {
        accessService.revokeAccess(entityId, accessId);
        return ResponseEntity.ok(ApiResponse.successEmpty(200, "Access revoked"));
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
        return ResponseEntity.ok(ApiResponse.success(200, "Entity context selected", response));
    }
}