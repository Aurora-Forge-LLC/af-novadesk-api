package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.RateSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full detail of a capital injection including associated ledger entries (LLR-FIN-02).
 */
@Schema(description = "Full capital injection detail with ledger entries")
public record CapitalInjectionDetailDto(

        @Schema(description = "Unique capital injection identifier")
        UUID id,

        @Schema(description = "Journal ID shared by all ledger entries in this batch")
        UUID journalId,

        @Schema(description = "Transfer ID linking inter-entity ledgers; null for standard injections")
        UUID transferId,

        @Schema(description = "Target legal entity code", example = "INDIA")
        String targetEntityCode,

        @Schema(description = "Target legal entity name", example = "India Operations")
        String targetEntityName,

        @Schema(description = "Source legal entity code for inter-entity transfers", example = "US")
        String sourceEntityCode,

        @Schema(description = "Funding source")
        FundingSource fundingSource,

        @Schema(description = "Funding date", example = "2026-05-18")
        LocalDate fundingDate,

        @Schema(description = "Amount in local currency", example = "100000.0000")
        BigDecimal amountLocal,

        @Schema(description = "Local currency ISO 4217 code", example = "INR")
        String currencyLocal,

        @Schema(description = "USD equivalent", example = "1200.0000")
        BigDecimal amountUsd,

        @Schema(description = "Exchange rate applied", example = "0.012000")
        BigDecimal exchangeRateUsed,

        @Schema(description = "Date of the rate used for conversion")
        LocalDate rateDateUsed,

        @Schema(description = "How the rate was obtained")
        RateSource rateSource,

        @Schema(description = "Source account UUID")
        UUID sourceAccountId,

        @Schema(description = "Source account name", example = "Founders - Equity")
        String sourceAccountName,

        @Schema(description = "Destination account UUID")
        UUID destinationAccountId,

        @Schema(description = "Destination account name", example = "Bank - Operating")
        String destinationAccountName,

        @Schema(description = "Reference number", example = "VCH-2026-001")
        String referenceNumber,

        @Schema(description = "Notes")
        String notes,

        @Schema(description = "Current lifecycle status")
        CapitalInjectionStatus injectionStatus,

        @Schema(description = "Auth user ID of whoever created this injection")
        String createdBy,

        @Schema(description = "Display name (falls back to email) of whoever created this injection")
        String createdByName,

        @Schema(description = "Creation timestamp")
        LocalDateTime createdAt,

        @Schema(description = "Last update timestamp")
        LocalDateTime updatedAt,

        @Schema(description = "Ledger entries posted for this injection")
        List<LedgerEntrySummaryDto> ledgerEntries
) {
}
