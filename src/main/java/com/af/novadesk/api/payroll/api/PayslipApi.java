package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Payslips", description = "Payslip access and download — LLR-PAY-02.5")
@RequestMapping("/api/v1/payroll/payslips")
@SecurityRequirement(name = "bearerAuth")
public interface PayslipApi {

    @Operation(summary = "List payslips with optional filters and pagination", description = """
            Returns payslips filtered by optional parameters.
            EMPLOYEE role callers are automatically scoped to their own payslips.
            Admin/HR callers may filter by employeeId, batchId, date range, and download status.
            """)
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PageResponse<PayslipDto>>> listPayslips(
            @Parameter(description = "Filter by employee ID")                        @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Filter by payroll batch ID")                   @RequestParam(required = false) UUID batchId,
            @Parameter(description = "Filter by legal entity")                       @RequestParam(required = false) UUID legalEntityId,
            @Parameter(description = "Search by employee name (case-insensitive)")   @RequestParam(required = false) String q,
            @Parameter(description = "Pay period start on or after this date")       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Pay period start on or before this date")      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Filter by downloaded status")                  @RequestParam(required = false) Boolean isDownloaded,
            @Parameter(description = "Page number (0-based)")                        @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")                                    @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field (e.g. payPeriodStart, netPay)")     @RequestParam(defaultValue = "payPeriodStart") String sortBy,
            @Parameter(description = "Sort direction: ASC or DESC")                  @RequestParam(defaultValue = "DESC") String sortDir);

    @Operation(summary = "Get payslip detail")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<PayslipDto>> getPayslip(@PathVariable UUID id);

    @Operation(summary = "Download payslip PDF")
    @GetMapping("/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id);

    @Operation(summary = "List payslips by batch")
    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAuthority('payroll:write') or hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<List<PayslipDto>>> listByBatch(@PathVariable UUID batchId);
}
