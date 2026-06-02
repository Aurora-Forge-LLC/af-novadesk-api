package com.af.novadesk.api.payroll.dto;

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

    private LeaveType leaveType;

    private BigDecimal totalAllocated;      // Annual allocation
    private BigDecimal usedDays;            // Days already consumed
    private BigDecimal pendingDays;         // Days in pending requests
    private BigDecimal availableDays;       // Computed: totalAllocated - usedDays - pendingDays

    private LocalDate accrualStartDate;

    private UUID fiscalYearSettingId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
