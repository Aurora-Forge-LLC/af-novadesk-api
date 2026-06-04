package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.LeaveRequestApi;
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

    public LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
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
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listMyRequests(UUID employeeId) {
        List<LeaveRequestDto> result = (employeeId != null)
                ? leaveRequestService.listLeaveRequestsByEmployee(employeeId)
                : leaveRequestService.listLeaveRequestsByOrganization(getOrganizationIdFromJwt());
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> listPending(UUID approverId) {
        List<LeaveRequestDto> result = leaveRequestService.listPendingByApprover(approverId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> approveRequest(UUID id, @Valid LeaveRequestDto approval) {
        LeaveRequestDto result = leaveRequestService.approveLeaveRequest(id, approval);
        return ResponseBuilder.ok(result, "Leave request approved");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> rejectRequest(UUID id, @Valid LeaveRequestDto rejection) {
        LeaveRequestDto result = leaveRequestService.rejectLeaveRequest(id, rejection);
        return ResponseBuilder.ok(result, "Leave request rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<LeaveRequestDto>> requestModification(UUID id, @Valid LeaveRequestDto modification) {
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
}
