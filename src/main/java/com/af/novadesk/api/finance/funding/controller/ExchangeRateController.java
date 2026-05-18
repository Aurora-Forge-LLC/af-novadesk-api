package com.af.novadesk.api.finance.funding.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.funding.api.ExchangeRateApi;
import com.af.novadesk.api.finance.funding.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.funding.service.ExchangeRateReadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class ExchangeRateController implements ExchangeRateApi {

    private final ExchangeRateReadService exchangeRateReadService;

    public ExchangeRateController(ExchangeRateReadService exchangeRateReadService) {
        this.exchangeRateReadService = exchangeRateReadService;
    }

    @Override
    public ResponseEntity<ApiResponse<List<ExchangeRateSummaryResponse>>> list(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate
    ) {
        List<ExchangeRateSummaryResponse> data = exchangeRateReadService.list(sourceCurrency, targetCurrency, rateDate);

        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<ExchangeRateSummaryResponse>> getById(UUID id) {
        return ResponseBuilder.ok(exchangeRateReadService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }
}

