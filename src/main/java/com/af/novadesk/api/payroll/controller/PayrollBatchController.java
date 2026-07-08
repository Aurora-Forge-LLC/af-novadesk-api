package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.payroll.api.PayrollBatchApi;
import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.af.novadesk.api.payroll.dto.*;
import com.af.novadesk.api.payroll.service.PayrollBatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class PayrollBatchController implements PayrollBatchApi {

    private final PayrollBatchService payrollBatchService;
    private final EntityAccessGuard entityAccessGuard;

    public PayrollBatchController(PayrollBatchService payrollBatchService,
                                  EntityAccessGuard entityAccessGuard) {
        this.payrollBatchService = payrollBatchService;
        this.entityAccessGuard = entityAccessGuard;
    }

    /**
     * Validates that the caller may act on the given legal entity: an org-wide
     * role (SUPER_ADMIN/SYSTEM_ADMIN/ORG_ADMIN/ORG_*) or a holder of an ACTIVE
     * per-entity access grant (ENTITY_ADMIN, MANAGER, …). Delegates to the shared
     * {@link EntityAccessGuard} so scoping is consistent across every module.
     */
    private void validateEntityAccess(UUID legalEntityId) {
        entityAccessGuard.assertCanAccessEntity(legalEntityId);
    }

    private UUID getAuthUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal found");
    }

    // -------------------------------------------------------------------------
    // Endpoints
    // -------------------------------------------------------------------------

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
    public ResponseEntity<ApiResponse<PageResponse<PayrollBatchDto>>> listBatches(
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
            String sortDir) {
        UUID orgId = getOrganizationIdFromJwt();
        PageResponse<PayrollBatchDto> result = payrollBatchService.listBatchesFiltered(
                orgId, legalEntityId, batchStatus, currencyCode,
                payPeriodFrom, payPeriodTo, paymentDateFrom, paymentDateTo,
                page, size, sortBy, sortDir);
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
    public ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getFlaggedEmployeeLeaves(
            UUID batchId, UUID flaggedId) {
        List<LeaveRequestDto> result = payrollBatchService.getFlaggedEmployeeLeaveRequests(batchId, flaggedId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollFlaggedEmployeeDto>> processFlagged(
            UUID batchId, UUID flaggedId, @Valid PayrollFlaggedEmployeeDto action) {
        // Resolve batch to get legal entity for authorization check
        PayrollBatchDto batch = payrollBatchService.getPayrollBatch(batchId);
        validateEntityAccess(batch.getLegalEntityId());

        // Derive actionById server-side from JWT (prevents spoofing)
        UUID authUserId = getAuthUserIdFromJwt();
        action.setActionById(authUserId);

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
    public ResponseEntity<ApiResponse<PayrollBatchDto>> approvePayroll(UUID id, @Valid ApprovePayrollRequest approval) {
        PayrollBatchDto batch = payrollBatchService.getPayrollBatch(id);
        validateEntityAccess(batch.getLegalEntityId());

        if (approval == null) {
            approval = new ApprovePayrollRequest();
        }
        PayrollBatchDto result = payrollBatchService.approvePayroll(id, approval);
        return ResponseBuilder.ok(result, "Payroll approved");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> rejectPayroll(UUID id, @Valid RejectPayrollRequest rejection) {
        PayrollBatchDto batch = payrollBatchService.getPayrollBatch(id);
        validateEntityAccess(batch.getLegalEntityId());

        PayrollBatchDto result = payrollBatchService.rejectPayroll(id, rejection);
        return ResponseBuilder.ok(result, "Payroll rejected");
    }

    @Override
    public ResponseEntity<ApiResponse<PayrollBatchDto>> voidPayroll(UUID id, @Valid VoidPayrollRequest voidRequest) {
        PayrollBatchDto batch = payrollBatchService.getPayrollBatch(id);
        validateEntityAccess(batch.getLegalEntityId());

        PayrollBatchDto result = payrollBatchService.voidPayroll(id, voidRequest);
        return ResponseBuilder.ok(result, "Payroll voided");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> deletePayrollBatch(UUID id) {
        payrollBatchService.deletePayrollBatch(id);
        return ResponseBuilder.ok(null, "Payroll batch deleted");
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayrollLedgerEntryDto>>> getLedger(UUID id) {
        List<PayrollLedgerEntryDto> result = payrollBatchService.getLedgerEntries(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
