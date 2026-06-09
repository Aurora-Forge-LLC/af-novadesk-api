package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.common.entity.LegalEntity;

import java.util.List;

/**
 * Builds the default funding {@link Account} records (fa_accounts) from
 * country-specific templates (LLR-FIN-02.1).
 */
public interface AccountTemplateService {

    List<Account> buildFromCountry(CountryCode country, LegalEntity entity);
}
