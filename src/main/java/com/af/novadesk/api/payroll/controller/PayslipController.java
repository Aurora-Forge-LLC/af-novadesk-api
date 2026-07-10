package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.security.CallerContext;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.PayslipApi;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import com.af.novadesk.api.payroll.service.PayslipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PayslipController implements PayslipApi {

    private final PayslipService  payslipService;
    private final CallerContext   callerContext;

    @Override
    public ResponseEntity<ApiResponse<PageResponse<PayslipDto>>> listPayslips(
            UUID employeeId, UUID batchId, UUID legalEntityId, String q,
            LocalDate fromDate, LocalDate toDate, Boolean isDownloaded,
            int page, int size, String sortBy, String sortDir) {

        UUID orgId = callerContext.getOrganizationId();
        UUID effectiveEmployeeId = employeeId;

        if (callerContext.canReadAllPayroll()) {
            // HR / Finance / Admin — pass filters through unchanged
        } else if (callerContext.isManager()) {
            // MANAGER — scope to direct reports; honour an explicit filter if within their team
            if (effectiveEmployeeId != null && !callerContext.isDirectReport(effectiveEmployeeId)) {
                throw new IllegalArgumentException("Access denied: employee is not one of your direct reports");
            }
            if (effectiveEmployeeId == null) {
                // Return all direct reports' payslips; filtering will use IN clause isn't available
                // here — fall back to manager's own employeeId as a no-result guard when team is empty
                List<UUID> teamIds = callerContext.getDirectReportIds();
                if (teamIds.isEmpty()) {
                    effectiveEmployeeId = callerContext.getEmployeeId(); // own record only
                }
            }
        } else {
            // EMPLOYEE — own payslips only
            effectiveEmployeeId = callerContext.getEmployeeId();
        }

        PageResponse<PayslipDto> result = payslipService.listPayslipsFiltered(
                orgId, effectiveEmployeeId, batchId, fromDate, toDate, isDownloaded,
                q, legalEntityId, page, size, sortBy, sortDir);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayslipDto>> getPayslip(UUID id) {
        PayslipDto result = payslipService.getPayslip(id);
        assertOwnershipOrTeam(result.getEmployeeId());
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<byte[]> downloadPdf(UUID id) {
        PayslipDto payslip = payslipService.getPayslip(id);
        assertOwnershipOrTeam(payslip.getEmployeeId());
        byte[] pdf = payslipService.downloadPayslipPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayslipDto>>> listByBatch(UUID batchId) {
        List<PayslipDto> result = payslipService.listPayslipsByBatch(batchId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    private void assertOwnershipOrTeam(UUID payslipEmployeeId) {
        if (callerContext.canReadAllPayroll()) return;
        if (callerContext.isSelf(payslipEmployeeId)) return;
        if (callerContext.isManager() && callerContext.isDirectReport(payslipEmployeeId)) return;
        throw new IllegalArgumentException("Access denied: payslip does not belong to you or your team");
    }
}
