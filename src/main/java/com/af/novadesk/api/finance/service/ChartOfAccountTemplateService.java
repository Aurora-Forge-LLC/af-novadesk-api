package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.ChartOfAccount;

import java.util.List;

/**
 * Builds the default Chart of Accounts from country-specific templates (LLR-FIN-01.2).
 */
public interface ChartOfAccountTemplateService {

    List<ChartOfAccount> buildFromCountry(CountryCode country);
}
