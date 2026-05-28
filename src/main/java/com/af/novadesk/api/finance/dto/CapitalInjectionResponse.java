package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.RateSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response returned after successfully recording a capital injection (LLR-FIN-02).
 *
 * <p>Includes both local-currency and USD amounts, the exchange-rate provenance,
 * and the correlation IDs needed to locate the ledger entries.</p>
 */
@Builder
@Schema(description = "Capital injection confirmation response")
public record CapitalInjectionResponse(

        @Schema(description = "Unique ID of the capital injection record")
        UUID capitalInjectionId,

        @Schema(description = "Journal ID shared by all ledger entries in this posting batch")
        UUID journalId,

        @Schema(description = "Transfer ID linking both entity ledgers; null for non-inter-entity injections")
        UUID transferId,

        @Schema(description = "Entity code of the receiving entity", example = "INDIA")
        String targetEntityCode,

        @Schema(description = "Entity code of the sending entity; null for external funding sources")
        String sourceEntityCode,

        @Schema(description = "Injected amount in the entity's local currency", example = "100000.0000")
        BigDecimal amountLocal,

        @Schema(description = "ISO 4217 local currency code", example = "INR")
        String currencyLocal,

        @Schema(description = "USD equivalent at the time of injection", example = "1200.0000")
        BigDecimal amountUsd,

        @Schema(description = "Exchange rate applied (local → USD)", example = "0.012000")
        BigDecimal exchangeRateUsed,

        @Schema(description = "Date of the rate used for conversion")
        LocalDate rateDateUsed,

        @Schema(description = "How the exchange rate was obtained (API, MANUAL, LOOKBACK, IDENTITY)")
        RateSource rateSource,

        @Schema(description = "True when a lookback rate was used for USD conversion")
        Boolean rateWarning,

        @Schema(description = "Human-readable status message")
        String message
) {
}

