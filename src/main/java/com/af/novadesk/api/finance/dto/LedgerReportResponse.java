package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Paginated ledger report response (LLR-FIN-04.5).
 */
@Schema(description = "Paginated ledger report response")
public record LedgerReportResponse(

        @Schema(description = "Report rows for the current page")
        List<LedgerReportRow> rows,

        @Schema(description = "Current page (0-based)")
        int page,

        @Schema(description = "Page size")
        int size,

        @Schema(description = "Total number of rows across all pages")
        long totalElements,

        @Schema(description = "Total number of pages")
        int totalPages,

        @Schema(description = "Currency the amounts are shown in (USD or entity local)")
        String reportingCurrency,

        @Schema(description = "Entity code")
        String entityCode,

        @Schema(description = "Entity name")
        String entityName,

        @Schema(description = "Entity base currency", example = "INR")
        String entityBaseCurrency
) {
}
