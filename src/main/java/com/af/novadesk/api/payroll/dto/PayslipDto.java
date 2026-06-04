package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * Combined DTO for Payslip — aggregate root. Serves as request, response, and summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayslipDto {

    // Identity (response)
    private UUID id;
    private UUID payrollBatchId;
    private UUID legalEntityId;
    private String legalEntityName;

    // Employee
    private UUID employeeId;
    private String employeeName;
    private String employeeCode;
    private String department;

    // Period
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate paymentDate;

    // Attendance summary
    private Integer totalWorkingDays;
    private BigDecimal daysWorked;
    private BigDecimal paidLeaveDays;
    private BigDecimal sickLeaveDays;
    private BigDecimal unpaidLeaveDays;

    // Financial
    private BigDecimal grossSalary;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;

    // YTD totals
    private BigDecimal ytdGrossEarnings;
    private BigDecimal ytdTaxes;

    // Currency
    private String currencyCode;

    // PDF storage
    private String payslipPdfPath;
    private Boolean isDownloaded;

    // Children (detail views)
    private List<PayslipLineItemDto> lineItems;

    // Audit (response)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
