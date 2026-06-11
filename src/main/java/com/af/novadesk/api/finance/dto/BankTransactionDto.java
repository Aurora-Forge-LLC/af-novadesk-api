package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO representing a single parsed bank statement transaction.
 *
 * <p>Returned as part of {@link BankStatementDto} in upload and get-detail
 * responses so callers can see the extracted transaction data.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A single transaction parsed from a bank statement")
public class BankTransactionDto {

    @Schema(description = "Transaction date", example = "2026-01-02")
    private LocalDate transactionDate;

    @Schema(description = "Transaction description/narration", example = "Wire Transfer - Client A")
    private String description;

    @Schema(description = "Debit amount (money out). Null if credit-only row.", example = "1500.50")
    private BigDecimal debit;

    @Schema(description = "Credit amount (money in). Null if debit-only row.", example = "25000.00")
    private BigDecimal credit;

    @Schema(description = "Running balance after this transaction. May be null.", example = "123499.50")
    private BigDecimal balance;

    /**
     * Convenience: the signed amount (positive = credit, negative = debit).
     */
    @Schema(description = "Signed amount: positive for credit, negative for debit", example = "-1500.50")
    private BigDecimal signedAmount;
}
