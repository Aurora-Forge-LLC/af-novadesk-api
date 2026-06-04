package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.entity.AssetAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, UUID> {

    Optional<AssetAssignment> findByIdAndOrganizationId(UUID id, UUID orgId);

    /** Returns the currently active assignment for an asset (at most one). */
    @Query("""
           SELECT a FROM AssetAssignment a
           WHERE a.asset.id = :assetId
             AND a.assignmentStatus = 'ACTIVE'
           """)
    Optional<AssetAssignment> findActiveByAssetId(@Param("assetId") UUID assetId);

    /** All assignments for an employee — used for self-service portal and offboarding check. */
    @Query("""
           SELECT a FROM AssetAssignment a
           WHERE a.employeeId = :employeeId
             AND a.organizationId = :orgId
             AND a.assignmentStatus = :status
           """)
    List<AssetAssignment> findByEmployeeIdAndStatus(
            @Param("employeeId") UUID employeeId,
            @Param("orgId") UUID orgId,
            @Param("status") AssignmentStatus status);

    /** Offboarding gate — count unreturned assets for an employee. */
    @Query("""
           SELECT COUNT(a) FROM AssetAssignment a
           WHERE a.employeeId = :employeeId
             AND a.organizationId = :orgId
             AND a.assignmentStatus = 'ACTIVE'
           """)
    long countActiveByEmployeeId(@Param("employeeId") UUID employeeId, @Param("orgId") UUID orgId);

    Optional<AssetAssignment> findByAcknowledgmentToken(String token);
}
