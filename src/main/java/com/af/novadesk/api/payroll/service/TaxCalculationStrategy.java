package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.dto.PayslipLineItemDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Strategy interface for jurisdiction-specific tax calculation (LLR-PAY-04).
 * Each implementation handles a single jurisdiction's tax rules.
 */
public interface TaxCalculationStrategy {

    /**
     * Calculates tax deductions and employer expenses for an employee.
     *
     * @param employee    the employee entity
     * @param grossSalary the calculated gross salary for the pay period
     * @param taxConfig   the applicable tax configuration for the jurisdiction
     * @param ytdGross    year-to-date gross earnings (for annualized tax calculation)
     * @return list of payslip line items (DEDUCTION and EMPLOYER_EXPENSE)
     */
    List<PayslipLineItemDto> calculate(Employee employee, BigDecimal grossSalary,
                                        TaxConfiguration taxConfig, BigDecimal ytdGross);

    /**
     * @return the jurisdiction this strategy handles
     */
    Jurisdiction getSupportedJurisdiction();
}
