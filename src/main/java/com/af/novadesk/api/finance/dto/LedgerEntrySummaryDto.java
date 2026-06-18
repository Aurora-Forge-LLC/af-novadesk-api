package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.LedgerEntrySide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary of a single ledger entry for inclusion in capital injection detail view
 * (LLR-FIN-02.2).
 */
@Schema(description = "Single ledger entry summary")
public record LedgerEntrySummaryDto(

        @Schema(description = "Ledger entry UUID")
        UUID id,

        @Schema(description = "Account UUID")
        UUID accountId,

        @Schema(description = "Account name", example = "Founders - Equity")
        String accountName,

        @Schema(description = "Account code", example = "3001")
        String accountCode,

        @Schema(description = "DEBIT or CREDIT")
        LedgerEntrySide entrySide,

        @Schema(description = "Amount in local currency", example = "100000.0000")
        BigDecimal amountLocal,

        @Schema(description = "Local currency ISO 4217 code", example = "INR")
        String currencyLocal,

        @Schema(description = "USD equivalent", example = "1200.0000")
        BigDecimal amountUsd,

        @Schema(description = "Exchange rate used for conversion", example = "0.012000")
        BigDecimal exchangeRateUsed,

        @Schema(description = "Date of the rate used for USD conversion (may differ from transaction date if a lookback rate was used)", example = "2026-05-22")
        LocalDate rateDateUsed,

        @Schema(description = "True when a lookback rate was used instead of the exact transaction-date rate")
        boolean rateWarning,

        @Schema(description = "Description")
        String description
) {
}
