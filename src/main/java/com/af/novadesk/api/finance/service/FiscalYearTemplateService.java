package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
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
        log.info("Building fiscal year setting from template for country={}", country);
        // TODO: Implement fiscal year template loading per country
        FiscalYearSetting setting = new FiscalYearSetting();
        // Placeholder - in production, load from a template configuration
        return setting;
    }
}

