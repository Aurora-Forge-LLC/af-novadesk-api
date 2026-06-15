package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated list wrapper for {@link BankStatementDto}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated bank statement list response")
public class BankStatementPageDto {

    @Schema(description = "Statements on the current page")
    private List<BankStatementDto> content;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Number of records per page", example = "20")
    private int size;

    @Schema(description = "Total number of statements matching the query", example = "50")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private int totalPages;
}
