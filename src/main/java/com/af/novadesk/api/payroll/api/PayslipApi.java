package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Payslips", description = "Payslip access and download — LLR-PAY-02.5")
@RequestMapping("/api/v1/payroll/payslips")
@SecurityRequirement(name = "bearerAuth")
public interface PayslipApi {

    @Operation(summary = "List payslips", description = """
            Returns payslips filtered by optional employeeId and/or legalEntityId.
            - employeeId only: employee self-service (list my payslips)
            - legalEntityId only: entity-scoped listing (admin/HR dashboard)
            - both: payslips for a specific employee within an entity
            - neither: returns empty list (at least one filter is required)
            """)
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<ApiResponse<List<PayslipDto>>> listPayslips(
            @Parameter(description = "Employee ID (optional)") @RequestParam(required = false) UUID employeeId,
            @Parameter(description = "Legal Entity ID (optional)") @RequestParam(required = false) UUID legalEntityId);

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
    @PreAuthorize("hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<List<PayslipDto>>> listByBatch(@PathVariable UUID batchId);
}
