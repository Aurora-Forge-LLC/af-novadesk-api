package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A suggested match between a bank transaction and an expense for user review")
public class SuggestedMatchDto {

    @Schema(description = "Suggested match UUID")
    private UUID id;

    @Schema(description = "Bank transaction details")
    private BankTxnSummary bankTransaction;

    @Schema(description = "Suggested expense/ledger entry details")
    private ExpenseSummary suggestedExpense;

    @Schema(description = "Matching score 60-79")
    private Integer matchingScore;

    @Schema(description = "Score breakdown as JSON")
    private String scoreBreakdown;

    @Schema(description = "Who created the suggestion")
    private String suggestedBy;

    @Schema(description = "Current state: PENDING, ACCEPTED, REJECTED")
    private String suggestionStatus;

    @Schema(description = "When the suggestion was created")
    private LocalDateTime createdAt;

    // ── Inner summaries ────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of the bank transaction in a suggested match")
    public static class BankTxnSummary {
        private UUID id;
        private LocalDate transactionDate;
        private String description;
        private BigDecimal amount;
        private BigDecimal balance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Summary of the expense in a suggested match")
    public static class ExpenseSummary {
        private UUID id;
        private LocalDate expenseDate;
        private BigDecimal amount;
        private String vendorName;
        private String currencyCode;
    }
}
