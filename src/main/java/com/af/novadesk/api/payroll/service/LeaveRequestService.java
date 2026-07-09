package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.LeaveActionDto;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for Leave Request Management (LLR-PAY-01.2–01.5).
 */
public interface LeaveRequestService {

    // --- Leave Request Lifecycle ---

    LeaveRequestDto submitLeaveRequest(LeaveRequestDto request);

    LeaveRequestDto approveLeaveRequest(UUID requestId, LeaveActionDto approval);

    LeaveRequestDto rejectLeaveRequest(UUID requestId, LeaveActionDto rejection);

    LeaveRequestDto requestModification(UUID requestId, LeaveActionDto modification);

    LeaveRequestDto cancelLeaveRequest(UUID requestId);

    // --- Lifecycle Management ---

    /**
     * Auto-expires a stale pending leave request by restoring pending days
     * and transitioning it to {@link LeaveRequestStatus#EXPIRED}.
     * Called exclusively by the auto-expiry scheduled job.
     */
    LeaveRequestDto expireLeaveRequest(UUID requestId);

    // --- Queries ---

    LeaveRequestDto getLeaveRequest(UUID requestId);

    List<LeaveRequestDto> listLeaveRequestsByEmployee(UUID employeeId);

    List<LeaveRequestDto> listLeaveRequestsByOrganization(UUID organizationId);

    List<LeaveRequestDto> listLeaveRequestsByEntity(UUID legalEntityId);

    List<LeaveRequestDto> listPendingByApprover(UUID approverId);

    List<LeaveRequestDto> listPendingByEntity(UUID legalEntityId);

    PageResponse<LeaveRequestDto> listRequestsFiltered(
            UUID orgId,
            UUID employeeId,
            UUID legalEntityId,
            LeaveType leaveType,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            UUID approverId,
            int page,
            int size,
            String q,
            String sortBy,
            String sortDir);

    // --- Leave Balance ---

    LeaveBalanceDto getLeaveBalance(UUID employeeId, LeaveType type);

    List<LeaveBalanceDto> getLeaveBalancesByEmployee(UUID employeeId);

    LeaveBalanceDto initializeLeaveBalances(UUID employeeId, UUID legalEntityId);

    // --- Unpaid Leave Tracking ---

    /** Returns leave requests where unpaidDaysUsed > 0 for the given employee. */
    List<LeaveRequestDto> listUnpaidLeaveRequests(UUID employeeId);
}
