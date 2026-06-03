package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.BankAccountTemplate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.repository.BankAccountTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the default bank account templates from the {@code bank_account_templates}
 * database table (populated by Flyway migration). These templates are used
 * during entity approval to seed per-entity bank accounts (LLR-FIN-01.2).
 *
 * <p>Replaces the previous hard-coded switch-based approach. Adding a new
 * country or modifying accounts now requires only SQL changes — no Java code.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountTemplateService {

    private final BankAccountTemplateRepository templateRepository;

    /**
     * Loads the bank account template rows for the given country from the database.
     *
     * @param country the ISO alpha-2 country code
     * @return ordered list of BankAccountTemplate rows
     * @throws BadRequestException if the country is null or no template is found
     */
    public List<BankAccountTemplate> findByCountry(CountryCode country) {
        if (country == null) {
            throw new BadRequestException("Country code must not be null when loading bank account template");
        }
        List<BankAccountTemplate> templates =
                templateRepository.findByCountryCodeOrderBySortOrder(country.name());

        if (templates.isEmpty()) {
            throw new BadRequestException(
                    "No bank account template found for country: " + country);
        }
        log.info("Loaded {} bank account template entries for country={}", templates.size(), country);
        return templates;
    }
}
