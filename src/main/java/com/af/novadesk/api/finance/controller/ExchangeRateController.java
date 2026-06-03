package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.ExchangeRateApi;
import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateRequest;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import com.af.novadesk.api.finance.service.ExchangeRateWriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller implementing the {@link ExchangeRateApi} contract.
 * Handles both read and write operations for exchange rates (LLR-FIN-04).
 */
@RestController
public class ExchangeRateController implements ExchangeRateApi {

    private final ExchangeRateReadService exchangeRateReadService;
    private final ExchangeRateWriteService exchangeRateWriteService;

    public ExchangeRateController(
            ExchangeRateReadService exchangeRateReadService,
            ExchangeRateWriteService exchangeRateWriteService
    ) {
        this.exchangeRateReadService = exchangeRateReadService;
        this.exchangeRateWriteService = exchangeRateWriteService;
    }

    // =========================================================================
    // Read endpoints
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<List<ExchangeRateSummaryResponse>>> list(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate
    ) {
        List<ExchangeRateSummaryResponse> data = exchangeRateReadService.list(
                sourceCurrency, targetCurrency, rateDate);
        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> getById(UUID id) {
        ExchangeRateSummaryResponse summary = exchangeRateReadService.getById(id);
        ExchangeRateDetailResponse detail = new ExchangeRateDetailResponse(
                summary.id(),
                summary.sourceCurrency(),
                summary.targetCurrency(),
                summary.rateDate(),
                summary.exchangeRate(),
                summary.rateSource(),
                summary.status(),
                null,  // createdBy — not in summary
                null,  // approvedBy — not in summary
                summary.createdAt(),
                null   // updatedAt — not in summary
        );
        return ResponseBuilder.ok(detail, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    // =========================================================================
    // Write endpoints
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> create(
            ExchangeRateRequest request
    ) {
        // Caller identity is resolved internally via FinanceSecurityContext
        ExchangeRateDetailResponse result = exchangeRateWriteService.create(request, "controller");
        return ResponseBuilder.created(result, "Exchange rate created successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> update(
            UUID id,
            ExchangeRateRequest request
    ) {
        ExchangeRateDetailResponse result = exchangeRateWriteService.update(id, request);
        return ResponseBuilder.ok(result, "Exchange rate updated successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> approve(UUID id) {
        exchangeRateWriteService.approve(id, "controller");
        return ResponseBuilder.ok(null, "Exchange rate approved successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> delete(UUID id) {
        exchangeRateWriteService.softDelete(id);
        return ResponseBuilder.ok(null, "Exchange rate deleted successfully");
    }

    // =========================================================================
    // CSV Upload
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<CsvUploadResponse>> uploadCsv(MultipartFile file) {
        CsvUploadResponse result = exchangeRateWriteService.importCsv(file, "csv-upload");
        return ResponseBuilder.ok(result, "CSV import completed");
    }
}
