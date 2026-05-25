package com.af.novadesk.api.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paginated list wrapper for {@link ExpenseTransactionDto}.
 *
 * <p>Returned by {@code GET /api/v1/expense/transactions}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated expense transaction list response")
public class ExpenseTransactionPageDto {

    @Schema(description = "Expense transactions on the current page")
    private List<ExpenseTransactionDto> content;

    @Schema(description = "Current page number (0-based)", example = "0")
    private int page;

    @Schema(description = "Number of records per page", example = "20")
    private int size;

    @Schema(description = "Total number of transactions matching the query", example = "150")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "8")
    private int totalPages;
}
