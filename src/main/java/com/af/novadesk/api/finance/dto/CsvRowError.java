package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Summary of a single CSV row that could not be imported (LLR-FIN-04.2).
 */
@Schema(description = "CSV row import error detail")
public record CsvRowError(
        @Schema(description = "1-based line number in the CSV file")
        int lineNumber,

        @Schema(description = "Error description")
        String message
) {
}
