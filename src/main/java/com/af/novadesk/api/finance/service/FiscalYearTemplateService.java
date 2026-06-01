package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Manages Fiscal Year Settings templates per country (LLR-FIN-01.2).
 * 
 * <p>Provides country-specific fiscal year templates that are applied when
 * a legal entity is approved, unless overridden via the approval request.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalYearTemplateService {

    /**
     * Builds a FiscalYearSetting from the given country's template.
     *
     * @param country the country code
     * @return a new FiscalYearSetting instance configured for the country
     */
    public FiscalYearSetting buildFromCountry(CountryCode country) {
        if (country == null) {
            throw new BadRequestException(
                    "Country code must not be null when building fiscal year settings");
        }
        log.info("Building fiscal year setting from template for country={}", country);

        // LLR-FIN-01.2: Country-specific fiscal year templates.
        // These values are based on common regulatory requirements per country.
        return switch (country) {
            case US -> FiscalYearSetting.builder()
                    .fiscalStartMonth(1)    // January
                    .fiscalStartDay(1)
                    .fiscalEndMonth(12)     // December
                    .fiscalEndDay(31)
                    .currentFiscalYear(java.time.Year.now().getValue())
                    .periodsPerYear(12)     // Monthly accounting periods
                    .build();
            case IN -> FiscalYearSetting.builder()
                    .fiscalStartMonth(4)    // April
                    .fiscalStartDay(1)
                    .fiscalEndMonth(3)      // March
                    .fiscalEndDay(31)
                    .currentFiscalYear(java.time.Year.now().getValue())
                    .periodsPerYear(12)
                    .build();
            case NP -> FiscalYearSetting.builder()
                    .fiscalStartMonth(7)    // July (Bikram Sambat: mid-Jul to mid-Jul)
                    .fiscalStartDay(16)
                    .fiscalEndMonth(7)      // July
                    .fiscalEndDay(15)
                    .currentFiscalYear(java.time.Year.now().getValue())
                    .periodsPerYear(12)
                    .build();
        };
    }
}

