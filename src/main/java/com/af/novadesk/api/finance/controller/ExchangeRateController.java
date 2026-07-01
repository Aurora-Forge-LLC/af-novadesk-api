package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.ExchangeRateApi;
import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateRequest;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import com.af.novadesk.api.finance.service.ExchangeRateWriteService;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
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
    private final FinanceSecurityContext securityContext;
    private final ShadowUserRepository shadowUserRepository;

    public ExchangeRateController(
            ExchangeRateReadService exchangeRateReadService,
            ExchangeRateWriteService exchangeRateWriteService,
            FinanceSecurityContext securityContext,
            ShadowUserRepository shadowUserRepository
    ) {
        this.exchangeRateReadService = exchangeRateReadService;
        this.exchangeRateWriteService = exchangeRateWriteService;
        this.securityContext = securityContext;
        this.shadowUserRepository = shadowUserRepository;
    }

    private String resolveCallerName() {
        UUID authUserId = securityContext.getAuthUserId();
        return shadowUserRepository.findByAuthUserId(authUserId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getEmail())
                .orElseThrow(() -> new ShadowUserNotFoundException(authUserId));
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
        ExchangeRateDetailResponse detail = exchangeRateReadService.getById(id);
        return ResponseBuilder.ok(detail, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    // =========================================================================
    // Write endpoints
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> create(
            ExchangeRateRequest request
    ) {
        ExchangeRateDetailResponse result = exchangeRateWriteService.create(request, resolveCallerName());
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
        exchangeRateWriteService.approve(id, resolveCallerName());
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
        CsvUploadResponse result = exchangeRateWriteService.importCsv(file, resolveCallerName());
        return ResponseBuilder.ok(result, "CSV import completed");
    }
}
