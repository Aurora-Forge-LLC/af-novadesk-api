package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
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

        @Schema(description = "USD equivalent", example = "1200.0000")
        BigDecimal amountUsd,

        @Schema(description = "Description")
        String description
) {
}
