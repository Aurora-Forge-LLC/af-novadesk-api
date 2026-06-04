package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for PayrollLedgerEntry — immutable double-entry ledger record (PAY-03).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayrollLedgerEntryDto {

    private UUID id;

    /** Groups all entries in the same journal batch. */
    private UUID journalId;

    private UUID payrollBatchId;
    private UUID legalEntityId;

    /** Account code, e.g. "SALARY_EXPENSE", "EMPLOYEE_PAYABLE". */
    private String accountCode;

    private String accountDescription;

    /** DEBIT or CREDIT. */
    private String entrySide;

    private BigDecimal amount;

    /** ISO 4217 currency code. */
    private String currencyCode;

    /** USD equivalent for multi-currency entities. */
    private BigDecimal amountUsd;

    /** Exchange rate used for USD conversion. */
    private BigDecimal exchangeRateUsed;

    /** True for reversing entries on void. */
    private Boolean isReversal;

    /** Links reversal entry to original entry. */
    private UUID originalEntryId;

    /** Narrative, e.g. "March 2026 Salary Expense - Entity: Nepal Subsidiary". */
    private String description;

    /** Distinguishes from finance module ledger entries. */
    private String referenceType;

    /** PK of the PayrollBatch. */
    private UUID referenceId;

    private LocalDateTime createdAt;
}
