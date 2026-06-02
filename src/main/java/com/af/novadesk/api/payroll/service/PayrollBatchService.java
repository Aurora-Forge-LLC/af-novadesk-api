package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.dto.PayrollBatchDto;
import com.af.novadesk.api.payroll.dto.PayrollFlaggedEmployeeDto;
import com.af.novadesk.api.payroll.dto.PayrollLedgerEntryDto;

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

    PayrollBatchDto approvePayroll(UUID batchId, PayrollBatchDto approval);

    PayrollBatchDto rejectPayroll(UUID batchId, PayrollBatchDto rejection);

    PayrollBatchDto voidPayroll(UUID batchId, PayrollBatchDto voidRequest);

    // --- Queries ---

    PayrollBatchDto getPayrollBatch(UUID batchId);

    List<PayrollBatchDto> listPayrollBatchesByEntity(UUID legalEntityId);

    List<PayrollFlaggedEmployeeDto> listFlaggedEmployees(UUID batchId);

    List<PayrollLedgerEntryDto> getLedgerEntries(UUID batchId);

    List<PayrollLedgerEntryDto> getLedgerEntriesByJournal(UUID journalId);
}
