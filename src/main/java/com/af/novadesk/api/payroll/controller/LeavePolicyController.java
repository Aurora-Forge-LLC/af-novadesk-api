package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for leave policy CRUD endpoints. Authorization follows the same
 * pattern as {@link LeaveRequestController}: SUPER_ADMIN users can manage
 * policies for any entity in their organization; MANAGER users can manage
 * policies only for entities where they hold the MANAGER role.
 */
@RestController
public class LeavePolicyController implements LeavePolicyApi {

    private final LeavePolicyService leavePolicyService;
    private final EntityUserAccessRepository entityUserAccessRepository;

    public LeavePolicyController(LeavePolicyService leavePolicyService,
                                  EntityUserAccessRepository entityUserAccessRepository) {
        this.leavePolicyService = leavePolicyService;
        this.entityUserAccessRepository = entityUserAccessRepository;
    }

    // -------------------------------------------------------------------------
    // JWT Helpers
    // -------------------------------------------------------------------------

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

    private List<String> getRolesFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            List<String> roles = jwt.getClaim("roles");
            return roles != null ? roles : List.of();
        }
        return List.of();
    }

    private UUID getAuthUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal found");
    }

    /**
     * Validates that the caller has SUPER_ADMIN or entity-level MANAGER role
     * for the given legal entity. Throws SecurityException if not authorized.
     */
    private void validateEntityAccess(UUID legalEntityId) {
        List<String> roles = getRolesFromJwt();
        boolean isPrivileged = roles.stream().anyMatch(r ->
                "SUPER_ADMIN".equalsIgnoreCase(r)
                || "SYSTEM_ADMIN".equalsIgnoreCase(r)
                || "ORG_ADMIN".equalsIgnoreCase(r));

        if (isPrivileged) {
            return; // SUPER_ADMIN, SYSTEM_ADMIN, and ORG_ADMIN have full access
        }

        // Check entity-level MANAGER role
        UUID authUserId = getAuthUserIdFromJwt();
        Optional<EntityUserAccess> access = entityUserAccessRepository
                .findByShadowUserAuthUserIdAndLegalEntityId(authUserId, legalEntityId);
        if (access.isPresent()
                && access.get().getStatus() == Status.ACTIVE
                && "MANAGER".equals(access.get().getEntityRole())) {
            return; // MANAGER role on this entity
        }

        throw new SecurityException("Access denied: requires SUPER_ADMIN or entity-level MANAGER role");
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
