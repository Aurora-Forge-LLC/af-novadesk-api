package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.constants.LineItemType;
import com.af.novadesk.api.payroll.dto.PayslipLineItemDto;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;
import com.af.novadesk.api.payroll.entity.TaxSlab;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import com.af.novadesk.api.payroll.service.TaxCalculationStrategy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * India tax calculation: PF (12%+12%) + Professional Tax + Income Tax.
 */
@Service
public class IndiaTaxStrategy implements TaxCalculationStrategy {

    private final PayrollDetailsRepository payrollDetailsRepository;

    public IndiaTaxStrategy(PayrollDetailsRepository payrollDetailsRepository) {
        this.payrollDetailsRepository = payrollDetailsRepository;
    }

    @Override
    public List<PayslipLineItemDto> calculate(CmEmployee cmEmployee, BigDecimal grossSalary,
                                               TaxConfiguration taxConfig, BigDecimal ytdGross) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        // Resolve currency from PayrollDetails
        String currency = payrollDetailsRepository.findByEmployeeId(cmEmployee.getId())
                .map(PayrollDetails::getSalaryCurrency)
                .orElse("INR");

        int order = 100;

        // --- PF Employee Contribution (12% of Basic) ---
        BigDecimal pfCap = taxConfig.getPfMaxCapAmount() != null
                ? taxConfig.getPfMaxCapAmount() : new BigDecimal("15000");
        BigDecimal pfBase = grossSalary.min(pfCap);
        BigDecimal pfEmployeeRate = taxConfig.getPfEmployeeRate() != null
                ? taxConfig.getPfEmployeeRate() : new BigDecimal("0.12");
        BigDecimal pfEmployee = pfBase.multiply(pfEmployeeRate).setScale(4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("IN_PF_EMPLOYEE")
                .lineItemDescription("Provident Fund - Employee Contribution (12%)")
                .amount(pfEmployee)
                .currencyCode(currency)
                .displayOrder(order++)
                .build());

        // --- PF Employer Contribution (12%) ---
        BigDecimal pfEmployerRate = taxConfig.getPfEmployerRate() != null
                ? taxConfig.getPfEmployerRate() : new BigDecimal("0.12");
        BigDecimal pfEmployer = pfBase.multiply(pfEmployerRate).setScale(4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.EMPLOYER_EXPENSE)
                .lineItemCode("IN_PF_EMPLOYER")
                .lineItemDescription("Provident Fund - Employer Contribution (12%)")
                .amount(pfEmployer)
                .currencyCode(currency)
                .displayOrder(order++)
                .build());

        // --- Professional Tax ---
        if (taxConfig.getProfessionalTaxAmount() != null
                && taxConfig.getProfessionalTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            items.add(PayslipLineItemDto.builder()
                    .lineItemType(LineItemType.DEDUCTION)
                    .lineItemCode("IN_PROFESSIONAL_TAX")
                    .lineItemDescription("Professional Tax - " +
                            (taxConfig.getProfessionalTaxState() != null ? taxConfig.getProfessionalTaxState() : ""))
                    .amount(taxConfig.getProfessionalTaxAmount())
                    .currencyCode(currency)
                    .displayOrder(order++)
                    .build());
        }

        // --- Income Tax (Old Regime, Progressive, Annualized) ---
        BigDecimal annualGross = grossSalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTax = calculateProgressiveTax(annualGross, taxConfig.getTaxSlabs());
        BigDecimal monthlyTax = annualTax.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("IN_INCOME_TAX")
                .lineItemDescription("Income Tax")
                .amount(monthlyTax)
                .currencyCode(currency)
                .displayOrder(order)
                .build());

        return items;
    }

    private BigDecimal calculateProgressiveTax(BigDecimal annualIncome, List<TaxSlab> slabs) {
        BigDecimal totalTax = BigDecimal.ZERO;
        if (slabs == null || slabs.isEmpty()) return totalTax;

        for (TaxSlab slab : slabs) {
            BigDecimal slabFrom = slab.getIncomeFrom();
            BigDecimal slabTo = slab.getIncomeTo();
            BigDecimal rate = slab.getTaxRate();

            if (annualIncome.compareTo(slabFrom) <= 0) continue;

            BigDecimal taxableInSlab;
            if (slabTo == null || annualIncome.compareTo(slabTo) <= 0) {
                taxableInSlab = annualIncome.subtract(slabFrom);
            } else {
                taxableInSlab = slabTo.subtract(slabFrom);
            }

            totalTax = totalTax.add(taxableInSlab.multiply(rate));
        }

        return totalTax.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Jurisdiction getSupportedJurisdiction() {
        return Jurisdiction.INDIA;
    }
}
