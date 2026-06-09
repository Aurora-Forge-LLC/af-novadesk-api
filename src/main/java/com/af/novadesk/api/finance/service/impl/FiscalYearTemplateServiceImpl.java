package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.service.FiscalYearTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.Objects;

/**
 * Default implementation of {@link FiscalYearTemplateService}.
 * Provides country-specific fiscal year templates (LLR-FIN-01.2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalYearTemplateServiceImpl implements FiscalYearTemplateService {

    @Override
    public FiscalYearSetting buildFromCountry(CountryCode country) {
        Objects.requireNonNull(country, "Country must not be null");
        log.info("Building fiscal year setting from template for country={}", country);

        return switch (country) {
            case US -> FiscalYearSetting.builder()
                    .fiscalStartMonth(1)    // January
                    .fiscalStartDay(1)
                    .fiscalEndMonth(12)     // December
                    .fiscalEndDay(31)
                    .currentFiscalYear(Year.now().getValue())
                    .periodsPerYear(12)
                    .build();
            case IN -> FiscalYearSetting.builder()
                    .fiscalStartMonth(4)    // April
                    .fiscalStartDay(1)
                    .fiscalEndMonth(3)      // March
                    .fiscalEndDay(31)
                    .currentFiscalYear(Year.now().getValue())
                    .periodsPerYear(12)
                    .build();
            case NP -> FiscalYearSetting.builder()
                    .fiscalStartMonth(7)    // July (Bikram Sambat: mid-Jul to mid-Jul)
                    .fiscalStartDay(16)
                    .fiscalEndMonth(7)      // July
                    .fiscalEndDay(15)
                    .currentFiscalYear(Year.now().getValue())
                    .periodsPerYear(12)
                    .build();
        };
    }
}
