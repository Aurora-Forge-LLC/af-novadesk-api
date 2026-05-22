package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.RateSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Summary projection of a {@link com.af.novadesk.api.finance.entity.CapitalInjection}
 * for list views (LLR-FIN-02).
 */
@Schema(description = "Capital injection summary for list views")
public record CapitalInjectionSummaryDto(

        @Schema(description = "Unique capital injection identifier")
        UUID id,

        @Schema(description = "Target legal entity code", example = "INDIA")
        String targetEntityCode,

        @Schema(description = "Target legal entity name", example = "India Operations")
        String targetEntityName,

        @Schema(description = "Source legal entity code for inter-entity transfers", example = "US")
        String sourceEntityCode,

        @Schema(description = "Funding source", example = "FOUNDER_EQUITY")
        FundingSource fundingSource,

        @Schema(description = "Amount in local currency", example = "100000.0000")
        BigDecimal amountLocal,

        @Schema(description = "Local currency ISO 4217 code", example = "INR")
        String currencyLocal,

        @Schema(description = "USD equivalent", example = "1200.0000")
        BigDecimal amountUsd,

        @Schema(description = "Funding date", example = "2026-05-18")
        LocalDate fundingDate,

        @Schema(description = "Exchange rate applied", example = "0.012000")
        BigDecimal exchangeRateUsed,

        @Schema(description = "Rate source")
        RateSource rateSource,

        @Schema(description = "Current lifecycle status")
        CapitalInjectionStatus injectionStatus,

        @Schema(description = "Reference number", example = "VCH-2026-001")
        String referenceNumber,

        @Schema(description = "User who created this injection")
        String createdBy,

        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt
) {
}
