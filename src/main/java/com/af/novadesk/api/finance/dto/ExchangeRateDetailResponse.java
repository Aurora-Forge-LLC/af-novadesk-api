package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.RateSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Full detail response for a single exchange rate record (LLR-FIN-04.1).
 */
@Schema(description = "Exchange rate detail response")
public record ExchangeRateDetailResponse(

        @Schema(description = "Exchange-rate record ID")
        UUID id,

        @Schema(description = "Source currency", example = "INR")
        String sourceCurrency,

        @Schema(description = "Target currency", example = "USD")
        String targetCurrency,

        @Schema(description = "Rate date")
        LocalDate rateDate,

        @Schema(description = "Exchange rate", example = "0.012045")
        BigDecimal exchangeRate,

        @Schema(description = "Rate source")
        RateSource rateSource,

        @Schema(description = "Record status")
        Status status,

        @Schema(description = "User or system that created this rate")
        String createdBy,

        @Schema(description = "User who approved this manual rate")
        String approvedBy,

        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt,

        @Schema(description = "Last update timestamp")
        LocalDateTime updatedAt
) {
}
