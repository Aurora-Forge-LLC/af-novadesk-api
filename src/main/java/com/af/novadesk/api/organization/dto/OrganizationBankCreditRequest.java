package com.af.novadesk.api.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for crediting (depositing) or debiting (withdrawing) funds
 * into/from an organization bank account.
 *
 * <p>The amount is always a positive value. The endpoint semantics
 * (credit vs. debit) determine whether the amount is added to or
 * subtracted from the account balance.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Amount to credit or debit from the organization bank account")
public class OrganizationBankCreditRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Schema(description = "Amount to credit or debit (positive value)", example = "10000.0000")
    private BigDecimal amount;
}
