package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for splitting a single bank transaction into multiple expenses (LLR-BNK-03.5).
 *
 * <p>Validation: SUM(all split lines' amounts) must equal ABS(bank transaction amount)
 * within a tolerance of 0.001 to handle rounding.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Split a bank transaction into multiple expense entries")
public class SplitTransactionRequest {

    @NotNull(message = "Split lines are required")
    @Size(min = 2, message = "Must have at least 2 split lines")
    @Size(max = 20, message = "Maximum 20 split lines allowed")
    @Schema(description = "List of split line items")
    private List<SplitLineItem> lines;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Optional notes (max 500 chars)")
    private String notes;
}
