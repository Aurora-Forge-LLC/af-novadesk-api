package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.payroll.api.PayrollBatchApi;
import com.af.novadesk.api.payroll.dto.*;
import com.af.novadesk.api.payroll.service.PayrollBatchService;
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
public class PayrollBatchController implements PayrollBatchApi {

    private final PayrollBatchService payrollBatchService;
    private final EntityUserAccessRepository entityUserAccessRepository;

    public PayrollBatchController(PayrollBatchService payrollBatchService,
                                  EntityUserAccessRepository entityUserAccessRepository) {
        this.payrollBatchService = payrollBatchService;
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

        // Check entity-level MANAGER role via EntityUserAccess
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
