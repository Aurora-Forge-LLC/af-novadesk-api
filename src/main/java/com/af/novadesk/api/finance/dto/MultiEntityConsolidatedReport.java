package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consolidated multi-entity financial report (LLR-FIN-04.5).
 * Always denominated in USD for cross-entity consistency.
 */
@Schema(description = "Multi-entity consolidated report in USD")
public record MultiEntityConsolidatedReport(

        @Schema(description = "Entity-level breakdowns")
        List<EntitySummaryRow> entitySummaries,

        @Schema(description = "Grand total debits across all entities (USD)")
        BigDecimal totalDebitsUsd,

        @Schema(description = "Grand total credits across all entities (USD)")
        BigDecimal totalCreditsUsd,

        @Schema(description = "Net position (debits − credits) in USD")
        BigDecimal netPositionUsd,

        @Schema(description = "Report start date")
        LocalDate startDate,

        @Schema(description = "Report end date")
        LocalDate endDate
) {
}
