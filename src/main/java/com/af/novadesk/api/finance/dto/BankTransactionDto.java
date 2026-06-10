package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.MatchingMethod;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing an individual bank transaction with reconciliation status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionDto {

    private UUID id;
    private UUID statementId;
    private UUID legalEntityId;
    private UUID bankAccountId;
    private LocalDate transactionDate;
    private String description;
    private BigDecimal amount;
    private BigDecimal balance;
    private ReconciliationStatus reconciliationStatus;
    private UUID matchedLedgerEntryId;
    private Integer matchingScore;
    private MatchingMethod matchingMethod;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
