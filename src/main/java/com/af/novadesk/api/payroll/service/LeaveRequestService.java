package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.LeaveType;
import com.af.novadesk.api.payroll.dto.LeaveBalanceDto;
import com.af.novadesk.api.payroll.dto.LeaveRequestDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for Leave Request Management (LLR-PAY-01.2–01.5).
 */
public interface LeaveRequestService {

    // --- Leave Request Lifecycle ---

    LeaveRequestDto submitLeaveRequest(LeaveRequestDto request);

    LeaveRequestDto approveLeaveRequest(UUID requestId, LeaveRequestDto approval);

    LeaveRequestDto rejectLeaveRequest(UUID requestId, LeaveRequestDto rejection);

    LeaveRequestDto requestModification(UUID requestId, LeaveRequestDto modification);

    LeaveRequestDto cancelLeaveRequest(UUID requestId);

    // --- Queries ---

    LeaveRequestDto getLeaveRequest(UUID requestId);

    List<LeaveRequestDto> listLeaveRequestsByEmployee(UUID employeeId);

    List<LeaveRequestDto> listPendingByApprover(UUID approverId);

    List<LeaveRequestDto> listPendingByEntity(UUID legalEntityId);

    // --- Leave Balance ---

    LeaveBalanceDto getLeaveBalance(UUID employeeId, LeaveType type);

    List<LeaveBalanceDto> getLeaveBalancesByEmployee(UUID employeeId);

    LeaveBalanceDto initializeLeaveBalances(UUID employeeId, UUID legalEntityId);
}
