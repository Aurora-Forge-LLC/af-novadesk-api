package com.af.novadesk.api.asset.api;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.dto.AssetPayrollDeductionDto;
import com.af.novadesk.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only endpoints for asset payroll deductions — used by HR and Finance
 * to track DAMAGED/LOST asset deductions from employee pay.
 *
 * Base path: {@code /api/v1/assets/write-offs/deductions}
 */
@Tag(name = "Asset Payroll Deductions",
     description = "Track DAMAGED/LOST asset deductions applied to employee payslips")
@RequestMapping("/api/v1/assets/write-offs")
@SecurityRequirement(name = "bearerAuth")
public interface AssetPayrollDeductionApi {

    @Operation(summary = "List all asset payroll deductions for the organisation",
               description = "Filterable by status, employee, and date range. Returns PENDING or APPLIED deductions.")
    @GetMapping("/deductions")
    @PreAuthorize("hasAuthority('assets:manage') or hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<Page<AssetPayrollDeductionDto>>> listDeductions(
            @RequestParam(required = false) AssetDeductionStatus status,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "deductionDate") Pageable pageable);

    @Operation(summary = "Get the payroll deduction for a specific write-off",
               description = "Returns the deduction record if the write-off was approved with DEDUCT_FROM_PAY.")
    @GetMapping("/{writeOffId}/deduction")
    @PreAuthorize("hasAuthority('assets:manage') or hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<AssetPayrollDeductionDto>> getByWriteOffId(
            @PathVariable UUID writeOffId);
}
