package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import com.af.novadesk.api.payroll.constants.LeaveType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for LeaveBalance — read-only from API perspective.
 * Maps the employee's current state for a specific leave policy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeaveBalanceDto {

    private UUID id;
    private UUID legalEntityId;
    private UUID employeeId;
    private String employeeName;

    /** @deprecated Use leavePolicyName and paymentType instead. */
    @Deprecated
    private LeaveType leaveType;

    private UUID leavePolicyId;
    private String leavePolicyName;          // e.g. "Personal", "Sick"
    private LeavePaymentType paymentType;    // PAID or UNPAID

    private BigDecimal totalAllocated;      // Annual allocation
    private BigDecimal usedDays;            // Days already consumed
    private BigDecimal pendingDays;         // Days in pending requests
    private BigDecimal availableDays;       // Computed current available (earned + borrow - used - pending)
    private BigDecimal earnedDays;          // Cumulative earned days (for earned policies)
    private BigDecimal borrowLimit;         // Current borrow limit (for earned policies)
    private BigDecimal effectiveAvailable;  // availableDays + borrowLimit (total days employee can use)

    private LocalDate accrualStartDate;

    private UUID fiscalYearSettingId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
