package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
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
import java.util.UUID;

@RestController
public class LeaveRequestController implements LeaveRequestApi {

    private final LeaveRequestService leaveRequestService;
    private final EntityAccessGuard entityAccessGuard;
    private final CmEmployeeRepository cmEmployeeRepository;

    public LeaveRequestController(LeaveRequestService leaveRequestService,
                                  EntityAccessGuard entityAccessGuard,
                                  CmEmployeeRepository cmEmployeeRepository) {
        this.leaveRequestService = leaveRequestService;
        this.entityAccessGuard = entityAccessGuard;
        this.cmEmployeeRepository = cmEmployeeRepository;
    }

    // -------------------------------------------------------------------------
    // JWT helper methods
    // -------------------------------------------------------------------------

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
     * Extracts the {@code sub} claim (authUserId) from the authenticated JWT.
     */
    private UUID getAuthUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal found");
    }

    /**
     * Extracts the {@code permissions} claim from the authenticated JWT.
     */
    private List<String> getPermissionsFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            List<String> permissions = jwt.getClaim("permissions");
            return permissions != null ? permissions : List.of();
        }
        return List.of();
    }

    /**
     * True when the caller may view/act on OTHER employees' leave requests.
     * Only HR (holders of leave:approve or leave:manage) gets this — every
     * other role, regardless of tier, is scoped to its own leave requests.
     */
    private boolean canManageOthersLeave() {
        List<String> permissions = getPermissionsFromJwt();
        return permissions.contains("leave:approve") || permissions.contains("leave:manage");
    }

    /**
     * Resolves the authenticated user's (authUserId) corresponding employee record ID.
     */
    private UUID getMyEmployeeId() {
        UUID authUserId = getAuthUserIdFromJwt();
        UUID orgId = getOrganizationIdFromJwt();
        return cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .map(CmEmployee::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No employee record found for authenticated user " + authUserId));
    }

    /**
     * Returns the effective employeeId to use for a query.
     * Everyone but HR (leave:approve/leave:manage) is forced to their own
     * employee ID — non-HR roles cannot query anyone else's leave data.
     */
    private UUID resolveEmployeeId(UUID requestedEmployeeId) {
        if (!canManageOthersLeave()) {
            return getMyEmployeeId();
        }
        return requestedEmployeeId;
    }

    // -------------------------------------------------------------------------
    // Endpoint implementations
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> submitLeaveRequest(@Valid LeaveRequestDto request) {
        // Non-HR roles can only submit requests for themselves
        request.setEmployeeId(resolveEmployeeId(request.getEmployeeId()));
        LeaveRequestDto result = leaveRequestService.submitLeaveRequest(request);
        return ResponseBuilder.created(result, "Leave request submitted");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> getLeaveRequest(UUID id) {
        LeaveRequestDto result = leaveRequestService.getLeaveRequest(id);
        // Non-HR roles can only view their own requests
        if (!canManageOthersLeave()) {
            UUID myEmployeeId = getMyEmployeeId();
            if (!myEmployeeId.equals(result.getEmployeeId())) {
                throw new IllegalArgumentException("Access denied: leave request does not belong to you");
            }
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listMyRequests(UUID employeeId, UUID legalEntityId) {
        // Non-HR roles can only see their own requests
        UUID effectiveEmployeeId = resolveEmployeeId(employeeId);

        List<LeaveRequestDto> result;
        if (effectiveEmployeeId != null) {
            result = leaveRequestService.listLeaveRequestsByEmployee(effectiveEmployeeId);
        } else if (legalEntityId != null) {
            result = leaveRequestService.listLeaveRequestsByEntity(legalEntityId);
        } else {
            result = leaveRequestService.listLeaveRequestsByOrganization(getOrganizationIdFromJwt());
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(UUID approverId, UUID legalEntityId) {
        // Non-HR roles may only look up their own pending queue (as a courtesy
        // view — they still can't act on it, since approve/reject requires
        // leave:approve). They can never browse another approver's queue.
        if (approverId != null) {
            if (!canManageOthersLeave() && !approverId.equals(getMyEmployeeId())) {
                throw new IllegalArgumentException(
                        "Access denied: cannot view another employee's pending leave requests");
            }
            List<LeaveRequestDto> result = leaveRequestService.listPendingByApprover(approverId);
            return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
        }

        // approverId not provided — caller is listing pending requests for a whole
        // entity. Only HR (leave:approve/leave:manage) may see everyone's pending
        // requests; require a legalEntityId and confirm the caller may access it.
        if (!canManageOthersLeave()) {
            throw new IllegalArgumentException(
                    "Access denied: only HR may view entity-wide pending leave requests");
        }
        if (legalEntityId == null) {
            throw new IllegalArgumentException(
                    "legalEntityId is required when approverId is not provided");
        }
        entityAccessGuard.assertCanAccessEntity(legalEntityId);
        List<LeaveRequestDto> result = leaveRequestService.listPendingByEntity(legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> approveRequest(UUID id, @Valid LeaveActionDto approval) {
        assertCanActOnLeaveRequest(id);
        LeaveRequestDto result = leaveRequestService.approveLeaveRequest(id, approval);
        return ResponseBuilder.ok(result, "Leave request approved");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> rejectRequest(UUID id, @Valid LeaveActionDto rejection) {
        assertCanActOnLeaveRequest(id);
        LeaveRequestDto result = leaveRequestService.rejectLeaveRequest(id, rejection);
        return ResponseBuilder.ok(result, "Leave request rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> requestModification(UUID id, @Valid LeaveActionDto modification) {
        assertCanActOnLeaveRequest(id);
        LeaveRequestDto result = leaveRequestService.requestModification(id, modification);
        return ResponseBuilder.ok(result, "Modification requested");
    }

    /**
     * Confirms the caller may act on the leave request's legal entity before an
     * approve/reject/modify action. The {@code leave:approve} permission gate has
     * already run; this adds the per-entity scope check the endpoints previously
     * lacked entirely.
     */
    private void assertCanActOnLeaveRequest(UUID leaveRequestId) {
        LeaveRequestDto request = leaveRequestService.getLeaveRequest(leaveRequestId);
        entityAccessGuard.assertCanAccessEntity(request.getLegalEntityId());
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> cancelRequest(UUID id) {
        // Non-HR roles can only cancel their own requests
        if (!canManageOthersLeave()) {
            LeaveRequestDto existing = leaveRequestService.getLeaveRequest(id);
            UUID myEmployeeId = getMyEmployeeId();
            if (!myEmployeeId.equals(existing.getEmployeeId())) {
                throw new IllegalArgumentException("Access denied: leave request does not belong to you");
            }
        }
        LeaveRequestDto result = leaveRequestService.cancelLeaveRequest(id);
        return ResponseBuilder.ok(result, "Leave request cancelled");
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalances(UUID employeeId) {
        // Non-HR roles can only see their own balances
        UUID effectiveEmployeeId = resolveEmployeeId(employeeId);
        if (effectiveEmployeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        List<LeaveBalanceDto> result = leaveRequestService.getLeaveBalancesByEmployee(effectiveEmployeeId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveBalanceDto>>> getBalancesByPath(UUID employeeId) {
        return getBalances(employeeId);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listUnpaidRequests(UUID employeeId) {
        // Non-HR roles can only see their own unpaid requests
        UUID effectiveEmployeeId = resolveEmployeeId(employeeId);
        if (effectiveEmployeeId == null) {
            throw new IllegalArgumentException("employeeId is required");
        }
        List<LeaveRequestDto> result = leaveRequestService.listUnpaidLeaveRequests(effectiveEmployeeId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
