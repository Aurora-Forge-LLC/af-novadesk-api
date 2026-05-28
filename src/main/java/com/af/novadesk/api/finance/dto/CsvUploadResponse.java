package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Result of a CSV exchange-rate import operation (LLR-FIN-04.2).
 */
@Schema(description = "CSV upload result summary")
public record CsvUploadResponse(
        @Schema(description = "Total number of rows in the CSV file")
        int totalRows,

        @Schema(description = "Number of rates successfully imported")
        int successCount,

        @Schema(description = "Number of rows skipped (duplicate rates)")
        int skippedCount,

        @Schema(description = "Number of rows with validation errors")
        int errorCount,

        @Schema(description = "Detailed error list (empty when errorCount == 0)")
        List<CsvRowError> errors
) {
}
