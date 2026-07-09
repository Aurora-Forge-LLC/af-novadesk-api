package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.finance.constants.ExchangeRateApprovalStatus;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ExchangeRateReadService {

    PageResponse<ExchangeRateSummaryResponse> list(
            String sourceCurrency, String targetCurrency, LocalDate rateDate,
            LocalDate fromDate, LocalDate toDate,
            ExchangeRateApprovalStatus approvalStatus,
            RateSource rateSource, UUID legalEntityId,
            int page, int size, String sortBy, String sortDir);

    ExchangeRateDetailResponse getById(UUID id);
}

