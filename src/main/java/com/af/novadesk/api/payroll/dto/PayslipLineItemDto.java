package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LineItemType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Combined DTO for PayslipLineItem — child of Payslip.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayslipLineItemDto {

    private UUID id;
    private UUID payslipId;

    /** EARNING, DEDUCTION, or EMPLOYER_EXPENSE. */
    private LineItemType lineItemType;

    /** Code e.g. "NP_SSF_EMPLOYEE", "IN_PF_EMPLOYEE", "BASE_SALARY". */
    private String lineItemCode;

    /** Human-readable description, e.g. "Social Security Fund - Employee Contribution". */
    private String lineItemDescription;

    private BigDecimal amount;

    /** ISO 4217 currency code. */
    private String currencyCode;

    /** Sort order on the payslip. */
    private Integer displayOrder;
}
