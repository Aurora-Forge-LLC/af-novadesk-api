package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.payroll.api.LeaveRequestApi;
import com.af.novadesk.api.payroll.dto.LeaveActionDto;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;
import com.af.novadesk.api.payroll.service.LeaveRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
public class LeaveRequestController implements LeaveRequestApi {

    private final LeaveRequestService leaveRequestService;
    private final EntityUserAccessRepository entityUserAccessRepository;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
                                  EntityUserAccessRepository entityUserAccessRepository) {
        this.leaveRequestService = leaveRequestService;
        this.entityUserAccessRepository = entityUserAccessRepository;
    }

    /**
     * Extracts the {@code organizationId} claim from the authenticated JWT.
     */
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
     * Extracts the {@code roles} claim from the authenticated JWT.
     */
    private List<String> getRolesFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            List<String> roles = jwt.getClaim("roles");
            return roles != null ? roles : List.of();
        }
        return List.of();
    }

    /**
     * Extracts the {@code sub} claim (authUserId) from the authenticated JWT.
     */
    private UUID getAuthUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal found");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> submitLeaveRequest(@Valid LeaveRequestDto request) {
        LeaveRequestDto result = leaveRequestService.submitLeaveRequest(request);
        return ResponseBuilder.created(result, "Leave request submitted");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequest(UUID id) {
        LeaveRequestDto result = leaveRequestService.getLeaveRequest(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listMyRequests(UUID employeeId, UUID legalEntityId) {
        List<LeaveRequestDto> result;
        if (employeeId != null) {
            result = leaveRequestService.listLeaveRequestsByEmployee(employeeId);
        } else if (legalEntityId != null) {
            result = leaveRequestService.listLeaveRequestsByEntity(legalEntityId);
        } else {
            result = leaveRequestService.listLeaveRequestsByOrganization(getOrganizationIdFromJwt());
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(UUID approverId, UUID legalEntityId) {
        // If approverId is provided, use existing behavior (direct approver lookup)
        if (approverId != null) {
            List<LeaveRequestDto> result = leaveRequestService.listPendingByApprover(approverId);
            return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
        }

        // approverId not provided — check for privileged roles
        List<String> roles = getRolesFromJwt();
        boolean isSuperAdmin = roles.stream().anyMatch(r -> "SUPER_ADMIN".equalsIgnoreCase(r));

        if (isSuperAdmin) {
            if (legalEntityId == null) {
                throw new IllegalArgumentException(
                        "legalEntityId is required for SUPER_ADMIN users when approverId is not provided");
            }
            List<LeaveRequestDto> result = leaveRequestService.listPendingByEntity(legalEntityId);
            return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
        }

        // Check entity-level MANAGER role via EntityUserAccess
        if (legalEntityId != null) {
            UUID authUserId = getAuthUserIdFromJwt();
            Optional<EntityUserAccess> access = entityUserAccessRepository
                    .findByShadowUserAuthUserIdAndLegalEntityId(authUserId, legalEntityId);
            if (access.isPresent()
                    && access.get().getStatus() == Status.ACTIVE
                    && "MANAGER".equals(access.get().getEntityRole())) {
                List<LeaveRequestDto> result = leaveRequestService.listPendingByEntity(legalEntityId);
                return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
            }
        }

        // Neither SUPER_ADMIN nor MANAGER — approverId is required
        throw new IllegalArgumentException(
                "approverId is required when the user is not SUPER_ADMIN or entity MANAGER");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> approveRequest(UUID id, @Valid LeaveActionDto approval) {
        LeaveRequestDto result = leaveRequestService.approveLeaveRequest(id, approval);
        return ResponseBuilder.ok(result, "Leave request approved");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> rejectRequest(UUID id, @Valid LeaveActionDto rejection) {
        LeaveRequestDto result = leaveRequestService.rejectLeaveRequest(id, rejection);
        return ResponseBuilder.ok(result, "Leave request rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> requestModification(UUID id, @Valid LeaveActionDto modification) {
        LeaveRequestDto result = leaveRequestService.requestModification(id, modification);
        return ResponseBuilder.ok(result, "Modification requested");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> cancelRequest(UUID id) {
        LeaveRequestDto result = leaveRequestService.cancelLeaveRequest(id);
        return ResponseBuilder.ok(result, "Leave request cancelled");
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalances(UUID employeeId) {
        List<LeaveBalanceDto> result = leaveRequestService.getLeaveBalancesByEmployee(employeeId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalancesByPath(UUID employeeId) {
        List<LeaveBalanceDto> result = leaveRequestService.getLeaveBalancesByEmployee(employeeId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listUnpaidRequests(UUID employeeId) {
        List<LeaveRequestDto> result = leaveRequestService.listUnpaidLeaveRequests(employeeId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
