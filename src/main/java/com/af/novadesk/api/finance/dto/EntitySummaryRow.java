package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Summary row for a single legal entity in a consolidated multi-entity report
 * (LLR-FIN-04.5). Always denominated in USD for cross-entity comparability.
 */
@Schema(description = "Entity summary row in consolidated report")
public record EntitySummaryRow(

        @Schema(description = "Entity code", example = "INDIA")
        String entityCode,

        @Schema(description = "Entity name", example = "India Operations")
        String entityName,

        @Schema(description = "Entity base currency", example = "INR")
        String baseCurrency,

        @Schema(description = "Total debits in local currency")
        BigDecimal totalDebitsLocal,

        @Schema(description = "Total credits in local currency")
        BigDecimal totalCreditsLocal,

        @Schema(description = "Total debits in USD")
        BigDecimal totalDebitsUsd,

        @Schema(description = "Total credits in USD")
        BigDecimal totalCreditsUsd
) {
}
