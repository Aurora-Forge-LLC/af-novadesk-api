package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.common.constants.LedgerEntrySide;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Single row in a ledger report (LLR-FIN-04.5).
 *
 * <p>The {@code amount} field reflects the selected currency:
 * either {@code amount_usd} (USD view) or {@code amount_local} (local view).
 */
@Schema(description = "Single ledger report row")
public record LedgerReportRow(

        @Schema(description = "Ledger entry UUID")
        UUID entryId,

        @Schema(description = "Entry date")
        LocalDate entryDate,

        @Schema(description = "Account name")
        String accountName,

        @Schema(description = "Account code")
        String accountCode,

        @Schema(description = "DEBIT or CREDIT")
        LedgerEntrySide entrySide,

        @Schema(description = "Amount in the selected currency")
        BigDecimal amount,

        @Schema(description = "Currency code of the amount shown")
        String currency,

        @Schema(description = "Description")
        String description,

        @Schema(description = "Reference type (e.g., CAPITAL_INJECTION)")
        String referenceType,

        @Schema(description = "Reference document ID")
        UUID referenceId,

        @Schema(description = "Exchange rate used for USD conversion")
        BigDecimal exchangeRateUsed,

        @Schema(description = "True when a lookback rate was used")
        boolean rateWarning
) {
}
