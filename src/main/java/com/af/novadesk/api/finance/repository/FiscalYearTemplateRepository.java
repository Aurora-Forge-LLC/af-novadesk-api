package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.FiscalYearTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@code fiscal_year_templates} reference-data table.
 */
@Repository
public interface FiscalYearTemplateRepository extends JpaRepository<FiscalYearTemplate, UUID> {

    /**
     * Finds the fiscal year template for a given country code.
     *
     * @param countryCode ISO alpha-2 country code (e.g. "US", "IN", "NP")
     * @return Optional containing the template, or empty if not found
     */
    Optional<FiscalYearTemplate> findByCountryCode(String countryCode);
}
