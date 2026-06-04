package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeaveType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for LeaveTransaction — immutable audit trail, read-only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaveTransactionDto {

    private UUID id;
    private UUID leaveRequestId;
    private UUID legalEntityId;
    private UUID employeeId;
    private String employeeName;

    private LeaveType leaveType;

    /**
     * Positive = deduction from balance, Negative = restoration to balance.
     */
    private BigDecimal daysChange;

    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;

    /** DEDUCTION, RESTORATION, or ALLOCATION. */
    private String transactionType;

    private String description;

    private LocalDateTime createdAt;
}
