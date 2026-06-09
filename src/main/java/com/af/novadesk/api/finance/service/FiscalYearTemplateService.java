package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.common.entity.FiscalYearSetting;

/**
 * Builds country-specific fiscal year settings templates (LLR-FIN-01.2).
 */
public interface FiscalYearTemplateService {

    FiscalYearSetting buildFromCountry(CountryCode country);
}
