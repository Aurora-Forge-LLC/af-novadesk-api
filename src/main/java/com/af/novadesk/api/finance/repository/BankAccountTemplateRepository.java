package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.BankAccountTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@code bank_account_templates} reference-data table.
 */
@Repository
public interface BankAccountTemplateRepository extends JpaRepository<BankAccountTemplate, UUID> {

    /**
     * Finds all bank account templates for a given country code,
     * ordered by their display/processing sort order.
     *
     * @param countryCode ISO alpha-2 country code (e.g. "US", "IN", "NP")
     * @return ordered list of templates
     */
    List<BankAccountTemplate> findByCountryCodeOrderBySortOrder(String countryCode);
}
