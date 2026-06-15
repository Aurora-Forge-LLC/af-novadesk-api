package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single line item in a split transaction request (LLR-BNK-03.5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single split line with vendor, category, and amount")
public class SplitLineItem {

    @NotNull(message = "Vendor ID is required")
    @Schema(description = "Vendor/Payee UUID")
    private UUID vendorId;

    @NotNull(message = "Chart of account ID is required")
    @Schema(description = "Expense category UUID")
    private UUID chartOfAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Schema(description = "Split amount for this line item", example = "4000.00")
    private BigDecimal amount;
}
