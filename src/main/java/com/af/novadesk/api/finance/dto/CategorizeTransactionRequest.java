package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for manually categorizing an unmatched bank transaction (LLR-BNK-03.2).
 *
 * <p>Sent when a finance operator fills in the categorization form and submits.
 * The bank transaction ID is in the URL path.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Categorization form data for a bank transaction")
public class CategorizeTransactionRequest {

    @NotNull(message = "Vendor ID is required")
    @Schema(description = "Vendor/Payee UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID vendorId;

    @NotNull(message = "Chart of account ID is required")
    @Schema(description = "Expense category (chart of account) UUID", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID chartOfAccountId;

    @Schema(description = "Optional department")
    private String department;

    @Schema(description = "Optional project code")
    private String project;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Schema(description = "Optional notes (max 500 chars). If blank, bank transaction description is used.")
    private String notes;
}
