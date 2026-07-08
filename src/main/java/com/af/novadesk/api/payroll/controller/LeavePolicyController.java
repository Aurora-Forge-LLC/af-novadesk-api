package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.payroll.api.LeavePolicyApi;
import com.af.novadesk.api.payroll.dto.LeavePolicyDto;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;
import com.af.novadesk.api.payroll.service.LeavePolicyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for leave policy CRUD endpoints. Entity scoping is delegated to the
 * shared {@link EntityAccessGuard}: org-wide roles (SUPER_ADMIN/SYSTEM_ADMIN/
 * ORG_ADMIN/ORG_*) can manage policies for any entity in their organization;
 * everyone else must hold an ACTIVE per-entity access grant (ENTITY_ADMIN,
 * MANAGER, …) on the entity being managed.
 */
@RestController
public class LeavePolicyController implements LeavePolicyApi {

    private final LeavePolicyService leavePolicyService;
    private final EntityAccessGuard entityAccessGuard;

    public LeavePolicyController(LeavePolicyService leavePolicyService,
                                  EntityAccessGuard entityAccessGuard) {
        this.leavePolicyService = leavePolicyService;
        this.entityAccessGuard = entityAccessGuard;
    }

    private UUID getOrganizationIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String orgId = jwt.getClaimAsString("organizationId");
            if (orgId != null) {
                return UUID.fromString(orgId);
            }
        }
        throw new IllegalStateException("No organizationId claim found in JWT");
    }

    /**
     * Validates that the caller may manage leave policy for the given legal
     * entity, via the shared {@link EntityAccessGuard} (org-wide role or an
     * ACTIVE per-entity grant).
     */
    private void validateEntityAccess(UUID legalEntityId) {
        entityAccessGuard.assertCanAccessEntity(legalEntityId);
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<ApiResponse<LeavePolicyDto>> createPolicy(@Valid LeavePolicyRequest request) {
        validateEntityAccess(request.getLegalEntityId());
        LeavePolicyDto result = leavePolicyService.createPolicy(request);
        return ResponseBuilder.created(result, "Leave policy created");
    }

    @Override
    public ResponseEntity<ApiResponse<LeavePolicyDto>> updatePolicy(UUID id, @Valid LeavePolicyRequest request) {
        LeavePolicyDto existing = leavePolicyService.getPolicy(id);
        validateEntityAccess(existing.getLegalEntityId());
        LeavePolicyDto result = leavePolicyService.updatePolicy(id, request);
        return ResponseBuilder.ok(result, "Leave policy updated");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> deletePolicy(UUID id) {
        LeavePolicyDto existing = leavePolicyService.getPolicy(id);
        validateEntityAccess(existing.getLegalEntityId());
        leavePolicyService.deletePolicy(id);
        return ResponseBuilder.noContent("Leave policy deleted");
    }

    @Override
    public ResponseEntity<ApiResponse<LeavePolicyDto>> getPolicy(UUID id) {
        LeavePolicyDto result = leavePolicyService.getPolicy(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeavePolicyDto>>> listPolicies(UUID legalEntityId) {
        List<LeavePolicyDto> result;
        if (legalEntityId != null) {
            result = leavePolicyService.listPoliciesByEntity(legalEntityId);
        } else {
            result = leavePolicyService.listPoliciesByOrganization(getOrganizationIdFromJwt());
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeavePolicyDto>>> listPoliciesByEntity(UUID legalEntityId) {
        List<LeavePolicyDto> result = leavePolicyService.listPoliciesByEntity(legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
