package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.FlagAction;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for PayrollFlaggedEmployee — review queue item.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayrollFlaggedEmployeeDto {

    private UUID id;
    private UUID payrollBatchId;
    private UUID employeeId;
    private String employeeName;
    private String department;

    // Flag reason
    private BigDecimal unpaidLeaveDays;
    private BigDecimal unauthorizedAbsenceDays;
    private String flagReason;

    // Salary details
    private BigDecimal baseSalary;
    private BigDecimal calculatedSalary;        // Prorated amount
    private Integer totalWorkingDays;
    private BigDecimal daysWorked;

    // Executive action
    private FlagAction flagAction;

    /**
     * The UUID of the executive taking the action. Derived server-side from
     * the JWT. Populated with the {@code authUserId} (JWT {@code sub}) when
     * the actor is a SUPER_ADMIN without an employee record; otherwise
     * populated with the employee UUID.
     */
    private UUID actionById;
    private String actionByName;

    /**
     * Set when the acting user is not a {@code CmEmployee} (e.g. SUPER_ADMIN
     * who hasn't been onboarded). Contains the raw authUserId from the JWT.
     */
    private UUID actionByAuthUserId;
    private LocalDateTime actionAt;

    @Size(max = 500)
    private String actionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
