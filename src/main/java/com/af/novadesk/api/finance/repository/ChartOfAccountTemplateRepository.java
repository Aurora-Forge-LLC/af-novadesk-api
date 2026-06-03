package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.ChartOfAccountTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@code coa_templates} reference-data table.
 */
@Repository
public interface ChartOfAccountTemplateRepository extends JpaRepository<ChartOfAccountTemplate, UUID> {

    /**
     * Finds all Chart of Accounts templates for a given country code,
     * ordered by their display/processing sort order.
     *
     * @param countryCode ISO alpha-2 country code (e.g. "US", "IN", "NP")
     * @return ordered list of templates
     */
    List<ChartOfAccountTemplate> findByCountryCodeOrderBySortOrder(String countryCode);
}
