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
 * Nepal tax calculation: SSF (31%) + IRD progressive income tax.
 */
@Service
public class NepalTaxStrategy implements TaxCalculationStrategy {

    private final PayrollDetailsRepository payrollDetailsRepository;

    public NepalTaxStrategy(PayrollDetailsRepository payrollDetailsRepository) {
        this.payrollDetailsRepository = payrollDetailsRepository;
    }

    @Override
    public List<PayslipLineItemDto> calculate(CmEmployee cmEmployee, BigDecimal grossSalary,
                                               TaxConfiguration taxConfig, BigDecimal ytdGross) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        // Resolve currency from PayrollDetails
        String currency = payrollDetailsRepository.findByEmployeeId(cmEmployee.getId())
                .map(PayrollDetails::getSalaryCurrency)
                .orElse("NPR");

        int order = 100;

        // --- SSF Employee Contribution (11%) ---
        BigDecimal ssfCap = taxConfig.getSsfMaxCapAmount() != null
                ? taxConfig.getSsfMaxCapAmount() : new BigDecimal("50000");
        BigDecimal ssfBase = grossSalary.min(ssfCap);
        BigDecimal ssfEmployeeRate = taxConfig.getSsfEmployeeRate() != null
                ? taxConfig.getSsfEmployeeRate() : new BigDecimal("0.11");
        BigDecimal ssfEmployee = ssfBase.multiply(ssfEmployeeRate).setScale(4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("NP_SSF_EMPLOYEE")
                .lineItemDescription("Social Security Fund - Employee Contribution (11%)")
                .amount(ssfEmployee)
                .currencyCode(currency)
                .displayOrder(order++)
                .build());

        // --- SSF Employer Contribution (20%) ---
        BigDecimal ssfEmployerRate = taxConfig.getSsfEmployerRate() != null
                ? taxConfig.getSsfEmployerRate() : new BigDecimal("0.20");
        BigDecimal ssfEmployer = ssfBase.multiply(ssfEmployerRate).setScale(4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.EMPLOYER_EXPENSE)
                .lineItemCode("NP_SSF_EMPLOYER")
                .lineItemDescription("Social Security Fund - Employer Contribution (20%)")
                .amount(ssfEmployer)
                .currencyCode(currency)
                .displayOrder(order++)
                .build());

        // --- IRD Income Tax (Progressive, Annualized) ---
        BigDecimal annualGross = grossSalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTax = calculateProgressiveTax(annualGross, taxConfig.getTaxSlabs());
        BigDecimal monthlyTax = annualTax.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("NP_INCOME_TAX")
                .lineItemDescription("Income Tax (IRD Progressive)")
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
            BigDecimal rate = slab.getRatePercent();

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
        return Jurisdiction.NEPAL;
    }
}
