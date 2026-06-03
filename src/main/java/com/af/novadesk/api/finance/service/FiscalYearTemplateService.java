package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.FiscalYearTemplate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.repository.FiscalYearTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reads the default fiscal year templates from the {@code fiscal_year_templates}
 * database table (populated by Flyway migration). These templates are used
 * during entity approval to initialise fiscal year settings (LLR-FIN-01.2).
 *
 * <p>Replaces the previous hard-coded switch-based approach. Adding a new
 * country or modifying settings now requires only SQL changes — no Java code.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalYearTemplateService {

    private final FiscalYearTemplateRepository templateRepository;

    /**
     * Loads the fiscal year template row for the given country from the database.
     *
     * @param country the ISO alpha-2 country code
     * @return the FiscalYearTemplate row
     * @throws BadRequestException if the country is null or no template is found
     */
    public FiscalYearTemplate findByCountry(CountryCode country) {
        if (country == null) {
            throw new BadRequestException("Country code must not be null when loading fiscal year template");
        }
        return templateRepository.findByCountryCode(country.name())
                .orElseThrow(() -> new BadRequestException(
                        "No fiscal year template found for country: " + country));
    }
}
