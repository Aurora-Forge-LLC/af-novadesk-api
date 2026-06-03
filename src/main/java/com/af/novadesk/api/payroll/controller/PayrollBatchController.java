package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.PayrollBatchApi;
import com.af.novadesk.api.payroll.dto.PayrollBatchDto;
import com.af.novadesk.api.payroll.dto.PayrollFlaggedEmployeeDto;
import com.af.novadesk.api.payroll.dto.PayrollLedgerEntryDto;
import com.af.novadesk.api.payroll.service.PayrollBatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PayrollBatchController implements PayrollBatchApi {

    private final PayrollBatchService payrollBatchService;

    public PayrollBatchController(PayrollBatchService payrollBatchService) {
        this.payrollBatchService = payrollBatchService;
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> initiatePayroll(@Valid PayrollBatchDto request) {
        PayrollBatchDto result = payrollBatchService.initiatePayroll(request);
        return ResponseBuilder.created(result, "Payroll batch initiated");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> getBatch(UUID id) {
        PayrollBatchDto result = payrollBatchService.getPayrollBatch(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayrollBatchDto>>> listBatches(UUID legalEntityId) {
        List<PayrollBatchDto> result = (legalEntityId != null)
                ? payrollBatchService.listPayrollBatchesByEntity(legalEntityId)
                : payrollBatchService.listAllPayrollBatches();
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> validateAttendance(UUID id) {
        PayrollBatchDto result = payrollBatchService.validateAttendanceAndFlag(id);
        return ResponseBuilder.ok(result, "Attendance validated and employees flagged");
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayrollFlaggedEmployeeDto>>> listFlagged(UUID id) {
        List<PayrollFlaggedEmployeeDto> result = payrollBatchService.listFlaggedEmployees(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollFlaggedEmployeeDto>> processFlagged(
            UUID batchId, UUID flaggedId, @Valid PayrollFlaggedEmployeeDto action) {
        PayrollFlaggedEmployeeDto result = payrollBatchService.processFlaggedEmployee(batchId, flaggedId, action);
        return ResponseBuilder.ok(result, "Flagged employee processed");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> calculateSalaries(UUID id) {
        PayrollBatchDto result = payrollBatchService.calculateSalaries(id);
        return ResponseBuilder.ok(result, "Salaries calculated");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> generatePayslips(UUID id) {
        PayrollBatchDto result = payrollBatchService.generatePayslips(id);
        return ResponseBuilder.ok(result, "Payslips generated");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> approvePayroll(UUID id, @Valid PayrollBatchDto approval) {
        PayrollBatchDto result = payrollBatchService.approvePayroll(id, approval);
        return ResponseBuilder.ok(result, "Payroll approved");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> rejectPayroll(UUID id, @Valid PayrollBatchDto rejection) {
        PayrollBatchDto result = payrollBatchService.rejectPayroll(id, rejection);
        return ResponseBuilder.ok(result, "Payroll rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> voidPayroll(UUID id, @Valid PayrollBatchDto voidRequest) {
        PayrollBatchDto result = payrollBatchService.voidPayroll(id, voidRequest);
        return ResponseBuilder.ok(result, "Payroll voided");
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayrollLedgerEntryDto>>> getLedger(UUID id) {
        List<PayrollLedgerEntryDto> result = payrollBatchService.getLedgerEntries(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
