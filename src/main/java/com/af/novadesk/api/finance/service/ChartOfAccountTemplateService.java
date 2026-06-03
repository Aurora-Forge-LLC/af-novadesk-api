package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.ChartOfAccountTemplate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.repository.ChartOfAccountTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the default Chart of Accounts templates from the {@code coa_templates}
 * database table (populated by Flyway migration). These templates are used
 * during entity approval to seed per-entity Chart of Accounts (LLR-FIN-01.2).
 *
 * <p>Replaces the previous hard-coded switch-based approach. Adding a new
 * country or modifying accounts now requires only SQL changes — no Java code.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartOfAccountTemplateService {

    private final ChartOfAccountTemplateRepository templateRepository;

    /**
     * Loads the Chart of Accounts template rows for the given country from
     * the database.
     *
     * @param country the ISO alpha-2 country code
     * @return ordered list of ChartOfAccountTemplate rows
     * @throws BadRequestException if the country is null or no template is found
     */
    public List<ChartOfAccountTemplate> findByCountry(CountryCode country) {
        if (country == null) {
            throw new BadRequestException("Country code must not be null when loading Chart of Accounts template");
        }
        List<ChartOfAccountTemplate> templates =
                templateRepository.findByCountryCodeOrderBySortOrder(country.name());

        if (templates.isEmpty()) {
            throw new BadRequestException(
                    "No Chart of Accounts template found for country: " + country);
        }
        log.info("Loaded {} CoA template entries for country={}", templates.size(), country);
        return templates;
    }
}
