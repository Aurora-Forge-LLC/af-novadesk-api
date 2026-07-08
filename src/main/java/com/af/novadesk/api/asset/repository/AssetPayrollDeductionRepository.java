package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.entity.AssetPayrollDeduction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetPayrollDeductionRepository extends JpaRepository<AssetPayrollDeduction, UUID> {

    Optional<AssetPayrollDeduction> findByWriteOffId(UUID writeOffId);

    Optional<AssetPayrollDeduction> findByWriteOffIdAndOrganizationId(UUID writeOffId, UUID orgId);

    /**
     * Finds all PENDING deductions for a specific employee whose deductionDate
     * falls within the given payroll period. Used by the payroll module during
     * salary calculation.
     */
    @Query("""
           SELECT d FROM AssetPayrollDeduction d
           WHERE d.organizationId = :orgId
             AND d.employeeId = :employeeId
             AND d.deductionStatus = 'PENDING'
             AND d.deductionDate >= :periodStart
             AND d.deductionDate <= :periodEnd
           ORDER BY d.deductionDate ASC
           """)
    List<AssetPayrollDeduction> findPendingByEmployeeAndPeriod(
            @Param("orgId") UUID orgId,
            @Param("employeeId") UUID employeeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("""
           SELECT d FROM AssetPayrollDeduction d
           WHERE d.organizationId = :orgId
             AND (:status IS NULL OR d.deductionStatus = :status)
             AND (:employeeId IS NULL OR d.employeeId = :employeeId)
             AND (:from IS NULL OR d.deductionDate >= :from)
             AND (:to IS NULL OR d.deductionDate <= :to)
           ORDER BY d.deductionDate DESC
           """)
    Page<AssetPayrollDeduction> findAllFiltered(
            @Param("orgId") UUID orgId,
            @Param("status") AssetDeductionStatus status,
            @Param("employeeId") UUID employeeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);
}
