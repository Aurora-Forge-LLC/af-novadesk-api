package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request body for bulk-categorizing multiple unmatched bank transactions
 * with the same vendor and expense category (LLR-BNK-03.4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk categorization request — apply same vendor/category to multiple transactions")
public class BulkCategorizeRequest {

    @NotNull(message = "Transaction IDs list is required")
    @Size(min = 1, max = 100, message = "Must select between 1 and 100 transactions")
    @Schema(description = "List of bank transaction UUIDs to categorize")
    private List<UUID> transactionIds;

    @NotNull(message = "Vendor ID is required")
    @Schema(description = "Vendor/Payee UUID to apply to all transactions")
    private UUID vendorId;

    @NotNull(message = "Chart of account ID is required")
    @Schema(description = "Expense category UUID to apply to all transactions")
    private UUID chartOfAccountId;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Optional notes (max 500 chars)")
    private String notes;
}
