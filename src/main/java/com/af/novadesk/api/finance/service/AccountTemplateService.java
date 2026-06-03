package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FaAccountTemplate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.repository.FaAccountTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the default funding account templates from the {@code fa_account_templates}
 * database table (populated by Flyway migration). These templates are used
 * during entity approval to seed per-entity funding accounts (fa_accounts)
 * for the capital-injection workflow (LLR-FIN-02.1).
 *
 * <p>Replaces the previous hard-coded switch-based approach. Adding a new
 * country or modifying accounts now requires only SQL changes — no Java code.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountTemplateService {

    private final FaAccountTemplateRepository templateRepository;

    /**
     * Loads the funding account template rows for the given country from the database.
     *
     * @param country the ISO alpha-2 country code
     * @return ordered list of FaAccountTemplate rows
     * @throws BadRequestException if the country is null or no template is found
     */
    public List<FaAccountTemplate> findByCountry(CountryCode country) {
        if (country == null) {
            throw new BadRequestException("Country code must not be null when loading funding account template");
        }
        List<FaAccountTemplate> templates =
                templateRepository.findByCountryCodeOrderBySortOrder(country.name());

        if (templates.isEmpty()) {
            throw new BadRequestException(
                    "No funding account template found for country: " + country);
        }
        log.info("Loaded {} funding account template entries for country={}", templates.size(), country);
        return templates;
    }
}
