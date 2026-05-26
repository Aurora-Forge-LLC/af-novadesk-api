package com.af.novadesk.api.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO returned after a credit or debit operation on an
 * organization bank account.  Contains the account ID and the
 * updated balance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Updated balance after a credit/debit operation")
public class BankAccountBalanceResponse {

    @Schema(description = "Bank account record UUID", example = "00000000-0000-0000-0000-000000000001")
    private UUID id;

    @Schema(description = "Updated available balance", example = "25000.0000")
    private BigDecimal balance;
}
