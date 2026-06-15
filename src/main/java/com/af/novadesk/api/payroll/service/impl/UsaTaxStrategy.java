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
 * USA tax calculation: data-driven via TaxConfiguration records.
 *
 * <p>Supports two calculation methods per config:
 * <ul>
 *   <li><b>PROGRESSIVE</b> — slab-based progressive Federal/State income tax on annualized income</li>
 *   <li><b>FLAT_ON_CAP</b> — flat rate applied to capped monthly base (e.g., Social Security 6.2% on up to wage base)</li>
 * </ul>
 *
 * <p>Field mapping for USA TaxConfiguration records:
 * <ul>
 *   <li>{@code ssf_*} fields → Social Security (OASDI): employee rate, employer rate, wage base cap</li>
 *   <li>{@code pf_*} fields → Medicare (HI): employee rate, employer rate (no cap by default)</li>
 *   <li>{@code professionalTaxAmount/State} → Additional state-specific flat tax or surcharge</li>
 *   <li>{@code taxSlabs} → Progressive income tax brackets (Federal or State)</li>
 * </ul>
 */
@Service
public class UsaTaxStrategy implements TaxCalculationStrategy {

    private final PayrollDetailsRepository payrollDetailsRepository;

    public UsaTaxStrategy(PayrollDetailsRepository payrollDetailsRepository) {
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
                .orElse("USD");

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

            // Handle professional tax (state-specific flat amount) if configured
            if (config.getProfessionalTaxAmount() != null
                    && config.getProfessionalTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                String stateCode = config.getProfessionalTaxState() != null
                        ? config.getProfessionalTaxState() : "XX";
                String taxType = config.getTaxType() != null ? config.getTaxType() : "STATE";

                items.add(PayslipLineItemDto.builder()
                        .lineItemType(LineItemType.DEDUCTION)
                        .lineItemCode("US_" + taxType + "_STATE_TAX")
                        .lineItemDescription(config.getTaxName() != null
                                ? config.getTaxName()
                                : "State Tax (" + stateCode + ")")
                        .amount(config.getProfessionalTaxAmount())
                        .currencyCode(currency)
                        .displayOrder(order += 10)
                        .build());
            }
        }

        return items;
    }

    // --- FLAT_ON_CAP (unified — uses flat_* fields regardless of tax_type) ---

    private List<PayslipLineItemDto> calculateFlatOnCap(TaxConfiguration config, BigDecimal grossSalary,
                                                          String currency, int baseOrder) {
        List<PayslipLineItemDto> items = new ArrayList<>();

        BigDecimal employeeRate = config.getFlatEmployeeRate();
        BigDecimal employerRate = config.getFlatEmployerRate();
        BigDecimal cap = config.getFlatCapAmount();
        String taxType = config.getTaxType() != null ? config.getTaxType() : "FLAT";

        BigDecimal base = cap != null ? grossSalary.min(cap) : grossSalary;

        if (employeeRate != null && employeeRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal deduction = base.multiply(employeeRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            String description = config.getTaxName() != null
                    ? config.getTaxName() + " - Employee"
                    : "Flat Tax - Employee";

            items.add(PayslipLineItemDto.builder()
                    .lineItemType(LineItemType.DEDUCTION)
                    .lineItemCode("US_" + taxType + "_EMPLOYEE")
                    .lineItemDescription(description)
                    .amount(deduction)
                    .currencyCode(currency)
                    .displayOrder(baseOrder++)
                    .build());
        }

        if (employerRate != null && employerRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal employerContribution = base.multiply(employerRate)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            String description = config.getTaxName() != null
                    ? config.getTaxName() + " - Employer"
                    : "Flat Tax - Employer";

            items.add(PayslipLineItemDto.builder()
                    .lineItemType(LineItemType.EMPLOYER_EXPENSE)
                    .lineItemCode("US_" + taxType + "_EMPLOYER")
                    .lineItemDescription(description)
                    .amount(employerContribution)
                    .currencyCode(currency)
                    .displayOrder(baseOrder)
                    .build());
        }

        return items;
    }

    // --- PROGRESSIVE (Federal/State Income Tax) ---

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
                : "Income Tax";

        items.add(PayslipLineItemDto.builder()
                .lineItemType(LineItemType.DEDUCTION)
                .lineItemCode("US_" + taxType)
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
        return Jurisdiction.USA;
    }
}
