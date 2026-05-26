package com.af.novadesk.api.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for voiding a posted expense transaction.
 *
 * <p>A void reason is mandatory — it is stored in the outbox event payload
 * and the audit trail so the finance team can understand why the transaction
 * was reversed.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for voiding an expense transaction")
public class VoidExpenseDto {

    @NotBlank(message = "Void reason is required")
    @Size(max = 500, message = "Void reason must not exceed 500 characters")
    @Schema(
            description = "Mandatory reason explaining why this expense is being voided. " +
                          "Recorded in the audit trail and outbox event payload.",
            example = "Duplicate entry — original recorded under transaction #TXN-2026-00045"
    )
    @JsonProperty("void_reason")
    private String voidReason;
}
