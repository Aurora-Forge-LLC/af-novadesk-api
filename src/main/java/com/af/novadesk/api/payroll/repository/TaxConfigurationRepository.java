package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, UUID> {
    Optional<TaxConfiguration> findByLegalEntityIdAndJurisdiction(
            UUID legalEntityId, Jurisdiction jurisdiction);
    List<TaxConfiguration> findByLegalEntityId(UUID legalEntityId);
    List<TaxConfiguration> findByIsActiveTrue();
}
