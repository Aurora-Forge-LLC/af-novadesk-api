package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.entity.FiscalYearSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link FiscalYearSetting} entity.
 * Moved to {@code common} — shared across finance and payroll modules.
 */
@Repository
public interface FiscalYearSettingRepository extends JpaRepository<FiscalYearSetting, UUID> {

    Optional<FiscalYearSetting> findByLegalEntityId(UUID legalEntityId);

    boolean existsByLegalEntityId(UUID legalEntityId);
}
