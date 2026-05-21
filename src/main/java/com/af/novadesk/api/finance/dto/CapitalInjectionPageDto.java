package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Paginated wrapper for capital injection summaries (LLR-FIN-02).
 */
@Schema(description = "Paginated list of capital injections")
public record CapitalInjectionPageDto(

        @Schema(description = "List of capital injections on the current page")
        List<CapitalInjectionSummaryDto> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Page size", example = "20")
        int size,

        @Schema(description = "Total elements across all pages", example = "1")
        long totalElements,

        @Schema(description = "Total number of pages", example = "1")
        int totalPages
) {
}
