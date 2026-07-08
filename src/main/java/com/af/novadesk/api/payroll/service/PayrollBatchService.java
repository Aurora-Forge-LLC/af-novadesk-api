package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.af.novadesk.api.payroll.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for Payroll Generation with Leave Integration
 * (LLR-PAY-02, PAY-03).
 */
public interface PayrollBatchService {

    // --- Payroll Lifecycle ---

    PayrollBatchDto initiatePayroll(PayrollBatchDto request);

    PayrollBatchDto validateAttendanceAndFlag(UUID batchId);

    PayrollFlaggedEmployeeDto processFlaggedEmployee(UUID batchId, UUID flaggedId,
                                                     PayrollFlaggedEmployeeDto action);

    PayrollBatchDto calculateSalaries(UUID batchId);

    PayrollBatchDto generatePayslips(UUID batchId);

    PayrollBatchDto approvePayroll(UUID batchId, ApprovePayrollRequest approval);

    PayrollBatchDto rejectPayroll(UUID batchId, RejectPayrollRequest rejection);

    PayrollBatchDto voidPayroll(UUID batchId, VoidPayrollRequest voidRequest);

    void deletePayrollBatch(UUID batchId);

    // --- Queries ---

    PayrollBatchDto getPayrollBatch(UUID batchId);

    List<PayrollBatchDto> listAllPayrollBatches();

    List<PayrollBatchDto> listPayrollBatchesByEntity(UUID legalEntityId);

    PageResponse<PayrollBatchDto> listBatchesFiltered(
            UUID orgId,
            UUID legalEntityId,
            PayrollBatchStatus batchStatus,
            String currencyCode,
            LocalDate payPeriodFrom,
            LocalDate payPeriodTo,
            LocalDate paymentDateFrom,
            LocalDate paymentDateTo,
            int page,
            int size,
            String sortBy,
            String sortDir);

    List<PayrollFlaggedEmployeeDto> listFlaggedEmployees(UUID batchId);

    /**
     * Returns all approved unpaid leave requests that caused the given flagged
     * employee to appear in the review queue. Covers both UNPAID policy leaves
     * (Case A) and earned leave excess overflow (Case B: unpaidDaysUsed > 0).
     */
    List<LeaveRequestDto> getFlaggedEmployeeLeaveRequests(UUID batchId, UUID flaggedId);

    List<PayrollLedgerEntryDto> getLedgerEntries(UUID batchId);

    List<PayrollLedgerEntryDto> getLedgerEntriesByJournal(UUID journalId);
}
