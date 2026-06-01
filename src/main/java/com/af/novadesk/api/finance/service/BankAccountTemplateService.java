package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.EntityBankAccount;

import java.util.List;

/**
 * Builds default bank account records from country-specific templates (LLR-FIN-01.2).
 */
public interface BankAccountTemplateService {

    List<EntityBankAccount> buildFromCountry(CountryCode country);
}
