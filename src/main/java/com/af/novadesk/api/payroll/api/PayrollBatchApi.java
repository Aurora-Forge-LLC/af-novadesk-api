package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.PayrollBatchDto;
import com.af.novadesk.api.payroll.dto.PayrollFlaggedEmployeeDto;
import com.af.novadesk.api.payroll.dto.PayrollLedgerEntryDto;
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
            @Parameter(description = "Legal entity ID") @RequestParam UUID legalEntityId);

    @Operation(summary = "Validate attendance and flag employees")
    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> validateAttendance(@PathVariable UUID id);

    @Operation(summary = "List flagged employees")
    @GetMapping("/{id}/flagged")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<PayrollFlaggedEmployeeDto>>> listFlagged(@PathVariable UUID id);

    @Operation(summary = "Process flagged employee (waive/prorate)")
    @PatchMapping("/{batchId}/flagged/{flaggedId}")
    @PreAuthorize("hasAuthority('organizations:write')")
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

    @Operation(summary = "Approve payroll")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> approvePayroll(
            @PathVariable UUID id, @Valid @RequestBody PayrollBatchDto approval);

    @Operation(summary = "Reject payroll")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> rejectPayroll(
            @PathVariable UUID id, @Valid @RequestBody PayrollBatchDto rejection);

    @Operation(summary = "Void payroll")
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<PayrollBatchDto>> voidPayroll(
            @PathVariable UUID id, @Valid @RequestBody PayrollBatchDto voidRequest);

    @Operation(summary = "Get ledger entries")
    @GetMapping("/{id}/ledger")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<PayrollLedgerEntryDto>>> getLedger(@PathVariable UUID id);
}
