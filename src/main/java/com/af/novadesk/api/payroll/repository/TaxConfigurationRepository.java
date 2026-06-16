package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, UUID> {
    List<TaxConfiguration> findByLegalEntityIdAndJurisdiction(
            UUID legalEntityId, Jurisdiction jurisdiction);

    List<TaxConfiguration> findByLegalEntityIdAndJurisdictionAndIsActiveTrue(
            UUID legalEntityId, Jurisdiction jurisdiction);

    /**
     * Finds active tax configurations whose effective period overlaps with the given pay period.
     * A config is included if:
     * - effectiveFrom <= periodEnd AND
     * - (effectiveTo IS NULL OR effectiveTo >= periodStart)
     * This ensures only configurations valid during the pay period are applied.
     */
    @Query("SELECT tc FROM TaxConfiguration tc " +
           "WHERE tc.legalEntity.id = :entityId " +
           "  AND tc.jurisdiction = :jurisdiction " +
           "  AND tc.isActive = true " +
           "  AND tc.effectiveFrom <= :periodEnd " +
           "  AND (tc.effectiveTo IS NULL OR tc.effectiveTo >= :periodStart)")
    List<TaxConfiguration> findActiveByEntityAndJurisdictionForPeriod(
            @Param("entityId") UUID entityId,
            @Param("jurisdiction") Jurisdiction jurisdiction,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    List<TaxConfiguration> findByLegalEntityId(UUID legalEntityId);

    List<TaxConfiguration> findByLegalEntityIdAndIsActiveTrue(UUID legalEntityId);

    List<TaxConfiguration> findByIsActiveTrue();
}
