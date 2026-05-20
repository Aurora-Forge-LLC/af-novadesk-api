package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.common.constants.Status;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Exchange rate summary response")
public record ExchangeRateSummaryResponse(
        @Schema(description = "Exchange-rate record ID") UUID id,
        @Schema(description = "Source currency", example = "INR") String sourceCurrency,
        @Schema(description = "Target currency", example = "USD") String targetCurrency,
        @Schema(description = "Rate date") LocalDate rateDate,
        @Schema(description = "Exchange rate", example = "0.012000") BigDecimal exchangeRate,
        @Schema(description = "Rate source") RateSource rateSource,
        @Schema(description = "Record status") Status status,
        @Schema(description = "Created timestamp") LocalDateTime createdAt
) {
}

