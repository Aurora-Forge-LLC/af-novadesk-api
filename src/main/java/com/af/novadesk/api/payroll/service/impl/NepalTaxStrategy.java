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
 * Nepal tax calculation: data-driven via TaxConfiguration records.
 *
 * <p>Supports two calculation methods per config:
 * <ul>
 *   <li><b>PROGRESSIVE</b> — slab-based progressive IRD income tax on annualized income</li>
 *   <li><b>FLAT_ON_CAP</b> — flat rate applied to full monthly salary, with
 *       an optional cap on the resulting tax amount (e.g., SSF 11% with
 *       NPR 50,000 cap means min(11% × salary, NPR 50,000))</li>
 * </ul>
 *
 * <p>All active TaxConfigurations for the entity+jurisdiction are iterated.
 * Each config contributes one deduction line item (and optionally an employer expense line item).</p>
 */
@Service
public class NepalTaxStrategy implements TaxCalculationStrategy {

    private final PayrollDetailsRepository payrollDetailsRepository;

    public NepalTaxStrategy(PayrollDetailsRepository payrollDetailsRepository) {
        this.payrollDetailsRepository = payrollDetailsRepository;
    }

    @Override
    public List<PayslipLineItemDto> calculate(CmEmployee cmEmployee, BigDecimal grossSalary,
                                               List<TaxConfiguration> taxConfigs, BigDecimal ytdGross) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        if (taxConfigs == null || taxConfigs.isEmpty()) return items;

        // Resolve currency from PayrollDetails
        String currency = payrollDetailsRepository.findByEmployeeId(cmEmployee.getId())
                .map(PayrollDetails::getSalaryCurrency)
                .orElse("NPR");

        int order = 100;

        for (TaxConfiguration config : taxConfigs) {
            String method = config.getCalculationMethod() != null
                    ? config.getCalculationMethod() : "PROGRESSIVE";

            if ("FLAT_ON_CAP".equals(method)) {
                items.addAll(calculateFlatOnCap(config, grossSalary, currency, order));
                order += 10;
            } else {
                // PROGRESSIVE (default)
                items.addAll(calculateProgressive(config, grossSalary, currency, order));
                order += 10;
            }
        }

        return items;
    }

    // --- FLAT_ON_CAP (unified — uses flat_* fields regardless of tax_type) ---
    //
    // flat_cap_amount caps the RESULTING TAX AMOUNT, not the salary base.
    // Rate is applied to the full gross salary; if a cap is set, the tax
    // amount is clamped to that cap (monthly).

    private List<PayslipLineItemDto> calculateFlatOnCap(TaxConfiguration config, BigDecimal grossSalary,
                                                           String currency, int baseOrder) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        BigDecimal employeeRate = config.getFlatEmployeeRate();
        BigDecimal employerRate = config.getFlatEmployerRate();
        BigDecimal cap = config.getFlatCapAmount();

        String taxType = config.getTaxType() != null ? config.getTaxType() : "FLAT";

        if (employeeRate != null && employeeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deduction = grossSalary.multiply(employeeRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (cap != null) {
                deduction = deduction.min(cap);
            }
            String description = config.getTaxName() != null
                    ? config.getTaxName() + " - Employee"
                    : "Flat Tax - Employee";

            items.add(PayslipLineItemDto.builder()
                    .lineItemType(LineItemType.DEDUCTION)
                    .lineItemCode("NP_" + taxType + "_EMPLOYEE")
                    .lineItemDescription(description)
                    .amount(deduction)
                    .currencyCode(currency)
                    .displayOrder(baseOrder++)
                    .build());
        }

        if (employerRate != null && employerRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal employerContribution = grossSalary.multiply(employerRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (cap != null) {
                employerContribution = employerContribution.min(cap);
            }
            String description = config.getTaxName() != null
                    ? config.getTaxName() + " - Employer"
                    : "Flat Tax - Employer";

            items.add(PayslipLineItemDto.builder()
                    .lineItemType(LineItemType.EMPLOYER_EXPENSE)
                    .lineItemCode("NP_" + taxType + "_EMPLOYER")
                    .lineItemDescription(description)
                    .amount(employerContribution)
                    .currencyCode(currency)
                    .displayOrder(baseOrder)
                    .build());
        }

        return items;
    }

    // --- PROGRESSIVE (IRD Income Tax) ---

    private List<PayslipLineItemDto> calculateProgressive(TaxConfiguration config, BigDecimal grossSalary,
                                                           String currency, int baseOrder) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        List<TaxSlab> slabs = config.getTaxSlabs();
        if (slabs == null || slabs.isEmpty()) return items;

        BigDecimal annualGross = grossSalary.multiply(BigDecimal.valueOf(12));
        BigDecimal annualTax = calculateProgressiveTax(annualGross, slabs);
        BigDecimal monthlyTax = annualTax.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);

        if (monthlyTax.compareTo(BigDecimal.ZERO) <= 0) return items;

        String taxType = config.getTaxType() != null ? config.getTaxType() : "GENERAL";
        String description = config.getTaxName() != null
                ? config.getTaxName()
                : "Income Tax (IRD Progressive)";

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("NP_" + taxType)
                .lineItemDescription(description)
                .amount(monthlyTax)
                .currencyCode(currency)
                .displayOrder(baseOrder)
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

            totalTax = totalTax.add(taxableInSlab.multiply(rate).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }

        return totalTax.setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public Jurisdiction getSupportedJurisdiction() {
        return Jurisdiction.NEPAL;
    }
}
