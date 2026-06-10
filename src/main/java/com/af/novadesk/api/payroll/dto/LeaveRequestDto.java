package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
 * Combined DTO for LeaveRequest — serves as request, response, and summary.
 * Aggregate root DTO for the LeaveRequest aggregate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaveRequestDto {

    // Identity (response)
    private UUID id;
    private UUID legalEntityId;
    private UUID organizationId;

    // Employee
    @NotNull(message = "Employee ID is required")
    private UUID employeeId;
    private String employeeName;

    // Leave details
    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @NotNull(message = "Leave policy ID is required")
    private UUID leavePolicyId;
    private String leavePolicyName;       // response only

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date cannot be in the past")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @FutureOrPresent(message = "End date cannot be in the past")
    private LocalDate endDate;

    @NotNull(message = "Number of days is required")
    @Positive(message = "Number of days must be positive")
    private BigDecimal numberOfDays;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;

    @Size(max = 500, message = "Attachment path must be at most 500 characters")
    private String attachmentPath;

    // Balance snapshot (response)
    private BigDecimal paidBalanceBefore;
    private BigDecimal sickBalanceBefore;
    private BigDecimal paidDaysUsed;
    private BigDecimal sickDaysUsed;
    private BigDecimal unpaidDaysUsed;

    // Approval
    private UUID approverId;
    private String approverName;
    private UUID secondApproverId;
    private String secondApproverName;
    private UUID executiveApproverId;
    private String executiveApproverName;

    private LeaveRequestStatus leaveRequestStatus;

    @Size(max = 500)
    private String approverComment;

    // Warning message (response): "X days will be marked as Unpaid"
    private String warningMessage;

    // Lifecycle dates (response)
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime cancelledAt;

    // Children (included in detail views)
    private List<LeaveTransactionDto> transactions;

    // Audit (response)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
}
