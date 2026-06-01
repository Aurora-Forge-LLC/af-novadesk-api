package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Entity-level financial balance / available-capital snapshot (LLR-FIN-02.5).
 *
 * <p>Aggregates all POSTED capital injections and expenses for a single legal
 * entity and computes {@code netAvailable = totalCapitalInjected - totalExpenses}
 * in both the entity's local currency and USD.</p>
 */
@Schema(description = "Entity financial balance — capital injected vs expenses")
public record EntityBalanceResponse(

        @Schema(description = "Entity code", example = "INDIA")
        String entityCode,

        @Schema(description = "Entity legal name", example = "India Operations")
        String entityName,

        @Schema(description = "ISO 4217 base currency code", example = "INR")
        String baseCurrency,

        // ── Capital Injections ────────────────────────────────────────────────

        @Schema(description = "Total capital injected (local currency)", example = "500000.0000")
        BigDecimal totalCapitalInjectedLocal,

        @Schema(description = "Total capital injected (USD equivalent)", example = "6000.0000")
        BigDecimal totalCapitalInjectedUsd,

        // ── Expenses ──────────────────────────────────────────────────────────

        @Schema(description = "Total expenses incurred (local currency)", example = "125000.0000")
        BigDecimal totalExpensesLocal,

        @Schema(description = "Total expenses incurred (USD equivalent)", example = "1500.0000")
        BigDecimal totalExpensesUsd,

        // ── Net Position ──────────────────────────────────────────────────────

        @Schema(description = "Net available capital = injections − expenses (local currency)", example = "375000.0000")
        BigDecimal netAvailableLocal,

        @Schema(description = "Net available capital = injections − expenses (USD)", example = "4500.0000")
        BigDecimal netAvailableUsd
) {
}
