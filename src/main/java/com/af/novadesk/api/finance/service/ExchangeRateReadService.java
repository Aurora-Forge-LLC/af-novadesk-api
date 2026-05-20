package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExchangeRateReadService {

    List<ExchangeRateSummaryResponse> list(String sourceCurrency, String targetCurrency, LocalDate rateDate);

    ExchangeRateSummaryResponse getById(UUID id);
}

