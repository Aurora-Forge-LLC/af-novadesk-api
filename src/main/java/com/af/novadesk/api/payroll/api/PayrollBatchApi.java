package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Batches", description = "Payroll generation and approval — LLR-PAY-02, PAY-03")
@RequestMapping("/api/v1/payroll/batches")
@SecurityRequirement(name = "bearerAuth")
public interface PayrollBatchApi {

    @Operation(summary = "Initiate payroll batch")
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> initiatePayroll(@Valid @RequestBody PayrollBatchDto request);

    @Operation(summary = "Get payroll batch")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> getBatch(@PathVariable UUID id);

    @Operation(summary = "List batches by entity")
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<PayrollBatchDto>>> listBatches(
            @Parameter(description = "Optional legal entity ID to filter batches by entity. " +
                    "If omitted, returns batches for all entities.")
            @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "Validate attendance and flag employees")
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> validateAttendance(@PathVariable UUID id);

    @Operation(summary = "List flagged employees")
    @GetMapping("/{id}/flagged")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<PayrollFlaggedEmployeeDto>>> listFlagged(@PathVariable UUID id);

    @Operation(summary = "Get unpaid leave requests for a flagged employee")
    @GetMapping("/{batchId}/flagged/{flaggedId}/leaves")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getFlaggedEmployeeLeaves(
            @PathVariable UUID batchId, @PathVariable UUID flaggedId);

    @Operation(summary = "Process flagged employee (waive/prorate)",
               description = "SUPER_ADMIN (even without employee record) or entity-level MANAGER can process flagged employees. " +
                             "The actionById is derived server-side from the JWT.")
    @PatchMapping("/{batchId}/flagged/{flaggedId}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PayrollFlaggedEmployeeDto>> processFlagged(
            @PathVariable UUID batchId, @PathVariable UUID flaggedId,
            @Valid @RequestBody PayrollFlaggedEmployeeDto action);

    @Operation(summary = "Calculate salaries")
    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> calculateSalaries(@PathVariable UUID id);

    @Operation(summary = "Generate payslips")
    @PostMapping("/{id}/generate-payslips")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> generatePayslips(@PathVariable UUID id);

    @Operation(summary = "Approve payroll",
               description = "SUPER_ADMIN (even without employee record) or entity-level MANAGER can approve payroll.")
    @PostMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PayrollBatchDto>> approvePayroll(
            @PathVariable UUID id, @Valid @RequestBody ApprovePayrollRequest approval);

    @Operation(summary = "Reject payroll",
               description = "SUPER_ADMIN (even without employee record) or entity-level MANAGER can reject payroll.")
    @PostMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PayrollBatchDto>> rejectPayroll(
            @PathVariable UUID id, @Valid @RequestBody RejectPayrollRequest rejection);

    @Operation(summary = "Soft-delete payroll batch (INITIATED or REJECTED only)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> deletePayrollBatch(@PathVariable UUID id);

    @Operation(summary = "Void payroll",
               description = "SUPER_ADMIN (even without employee record) or entity-level MANAGER can void approved payroll.")
    @PostMapping("/{id}/void")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PayrollBatchDto>> voidPayroll(
            @PathVariable UUID id, @Valid @RequestBody VoidPayrollRequest voidRequest);

    @Operation(summary = "Get ledger entries")
    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<PayrollLedgerEntryDto>>> getLedger(@PathVariable UUID id);
}
