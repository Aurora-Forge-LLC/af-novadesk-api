package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Combined DTO for PayrollBatch — aggregate root. Serves as request, response, and summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayrollBatchDto {

    // Identity (response)
    private UUID id;
    private UUID legalEntityId;
    private String legalEntityName;

    // Period
    @NotNull(message = "Pay period start is required")
    private LocalDate payPeriodStart;

    @NotNull(message = "Pay period end is required")
    private LocalDate payPeriodEnd;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    // Summary counts (response)
    private Integer totalHeadcount;
    private Integer processedCount;
    private Integer flaggedCount;

    // Financial summary (response)
    private BigDecimal totalGrossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal totalNetPayout;

    // Currency
    private String currencyCode;
    private BigDecimal exchangeRateUsd;
    private BigDecimal totalNetPayoutUsd;

    // Status
    private PayrollBatchStatus batchStatus;

    // Approval
    private UUID approvedById;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String rejectionReason;

    // Ledger sync (PAY-03)
    private UUID journalId;
    private LocalDateTime ledgerPostedAt;

    // Children (detail views)
    private List<PayslipDto> payslips;
    private List<PayrollFlaggedEmployeeDto> flaggedEmployees;
    private List<PayrollLedgerEntryDto> ledgerEntries;

    // Pre-check results (response from initiate)
    private List<String> preCheckWarnings;

    // Audit (response)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
}
