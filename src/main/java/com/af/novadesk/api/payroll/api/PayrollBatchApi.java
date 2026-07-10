package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.af.novadesk.api.payroll.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Batches", description = "Payroll generation and approval — LLR-PAY-02, PAY-03")
@RequestMapping("/api/v1/payroll/batches")
@SecurityRequirement(name = "bearerAuth")
public interface PayrollBatchApi {

    @Operation(summary = "Initiate payroll batch")
    @PostMapping
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> initiatePayroll(@Valid @RequestBody PayrollBatchDto request);

    @Operation(summary = "Get payroll batch")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> getBatch(@PathVariable UUID id);

    @Operation(summary = "List payroll batches with optional filters and pagination")
    @GetMapping
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PageResponse<PayrollBatchDto>>> listBatches(
            @Parameter(description = "Filter by legal entity")                        @RequestParam(required = false) UUID legalEntityId,
            @Parameter(description = "Filter by batch status")                        @RequestParam(required = false) PayrollBatchStatus batchStatus,
            @Parameter(description = "Filter by currency code (e.g. USD)")            @RequestParam(required = false) String currencyCode,
            @Parameter(description = "Search by approver name (case-insensitive)")    @RequestParam(required = false) String q,
            @Parameter(description = "Pay period start on or after this date")        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payPeriodFrom,
            @Parameter(description = "Pay period end on or before this date")         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payPeriodTo,
            @Parameter(description = "Payment date on or after this date")            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDateFrom,
            @Parameter(description = "Payment date on or before this date")           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDateTo,
            @Parameter(description = "Page number (0-based)")                         @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")                                     @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field (e.g. createdAt, payPeriodStart)")   @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: ASC or DESC")                   @RequestParam(defaultValue = "DESC") String sortDir);

    @Operation(summary = "Validate attendance and flag employees")
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> validateAttendance(@PathVariable UUID id);

    @Operation(summary = "List flagged employees")
    @GetMapping("/{id}/flagged")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<List<PayrollFlaggedEmployeeDto>>> listFlagged(@PathVariable UUID id);

    @Operation(summary = "Get unpaid leave requests for a flagged employee")
    @GetMapping("/{batchId}/flagged/{flaggedId}/leaves")
    @PreAuthorize("hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<List<LeaveRequestDto>>> getFlaggedEmployeeLeaves(
            @PathVariable UUID batchId, @PathVariable UUID flaggedId);

    @Operation(summary = "Process flagged employee (waive/prorate)",
               description = "Requires payroll:prorate or payroll:waive permission. " +
                             "The actionById is derived server-side from the JWT.")
    @PatchMapping("/{batchId}/flagged/{flaggedId}")
    @PreAuthorize("hasAuthority('payroll:prorate') or hasAuthority('payroll:waive') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollFlaggedEmployeeDto>> processFlagged(
            @PathVariable UUID batchId, @PathVariable UUID flaggedId,
            @Valid @RequestBody PayrollFlaggedEmployeeDto action);

    @Operation(summary = "Calculate salaries")
    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> calculateSalaries(@PathVariable UUID id);

    @Operation(summary = "Generate payslips")
    @PostMapping("/{id}/generate-payslips")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> generatePayslips(@PathVariable UUID id);

    @Operation(summary = "Approve payroll",
               description = "Requires payroll:approve permission. " +
                             "Entity-level access is also verified at runtime.")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('payroll:approve')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> approvePayroll(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ApprovePayrollRequest approval);

    @Operation(summary = "Reject payroll",
               description = "Requires payroll:approve permission.")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('payroll:approve')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> rejectPayroll(
            @PathVariable UUID id, @Valid @RequestBody RejectPayrollRequest rejection);

    @Operation(summary = "Soft-delete payroll batch (INITIATED or REJECTED only)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:delete')")
    ResponseEntity<ApiResponse<Void>> deletePayrollBatch(@PathVariable UUID id);

    @Operation(summary = "Void payroll",
               description = "Requires payroll:delete permission.")
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('payroll:delete')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> voidPayroll(
            @PathVariable UUID id, @Valid @RequestBody VoidPayrollRequest voidRequest);

    @Operation(summary = "Get ledger entries")
    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<List<PayrollLedgerEntryDto>>> getLedger(@PathVariable UUID id);
}
