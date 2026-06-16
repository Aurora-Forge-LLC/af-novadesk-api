package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.PayslipApi;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import com.af.novadesk.api.payroll.service.PayslipService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PayslipController implements PayslipApi {

    private final PayslipService payslipService;

    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayslipDto>>> listPayslips(UUID employeeId, UUID legalEntityId) {
        List<PayslipDto> result = payslipService.listPayslips(employeeId, legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayslipDto>> getPayslip(UUID id) {
        PayslipDto result = payslipService.getPayslip(id);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<byte[]> downloadPdf(UUID id) {
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
}
