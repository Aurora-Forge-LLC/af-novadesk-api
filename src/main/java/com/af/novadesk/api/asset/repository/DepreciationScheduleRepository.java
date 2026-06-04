package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.entity.DepreciationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepreciationScheduleRepository extends JpaRepository<DepreciationSchedule, UUID> {

    List<DepreciationSchedule> findAllByAssetIdOrderByFiscalYearAsc(UUID assetId);

    Optional<DepreciationSchedule> findByAssetIdAndFiscalYear(UUID assetId, int fiscalYear);

    /** Unposted schedules for a fiscal year — used by the year-end scheduler. */
    @Query("""
           SELECT d FROM DepreciationSchedule d
           WHERE d.organizationId = :orgId
             AND d.fiscalYear = :year
             AND d.posted = false
           """)
    List<DepreciationSchedule> findUnpostedByOrgAndYear(
            @Param("orgId") UUID orgId,
            @Param("year") int year);
}
