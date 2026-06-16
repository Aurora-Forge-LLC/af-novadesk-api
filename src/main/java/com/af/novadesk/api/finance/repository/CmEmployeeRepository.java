package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmEmployeeRepository extends JpaRepository<CmEmployee, UUID> {

    Optional<CmEmployee> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<CmEmployee> findByAuthUserIdAndOrganizationId(UUID authUserId, UUID organizationId);

    boolean existsByAuthUserIdAndOrganizationId(UUID authUserId, UUID organizationId);

    /** All employees in an org with a given status. */
    List<CmEmployee> findAllByOrganizationIdAndEmployeeStatus(
            UUID organizationId, EmployeeStatus status);

    /** All employees assigned to a specific legal entity — for asset assignment dropdown. */
    @Query("""
           SELECT e FROM CmEmployee e
           JOIN CmEmployeeEntityAssignment a ON a.employee = e
           WHERE a.legalEntity.id  = :entityId
             AND a.assignmentStatus = 'ACTIVE'
             AND e.employeeStatus  = :status
           ORDER BY e.displayName
           """)
    List<CmEmployee> findAllByLegalEntityIdAndStatus(
            @Param("entityId") UUID entityId,
            @Param("status")   EmployeeStatus status);

    /** All employees assigned to a specific legal entity, regardless of employee status. */
    @Query("""
           SELECT e FROM CmEmployee e
           JOIN CmEmployeeEntityAssignment a ON a.employee = e
           WHERE a.legalEntity.id  = :entityId
             AND a.assignmentStatus = 'ACTIVE'
           ORDER BY e.displayName
           """)
    List<CmEmployee> findAllByLegalEntityId(
            @Param("entityId") UUID entityId);

    /** Look up by employee code scoped to a legal entity — used by getEmployeeByCode. */
    @Query("""
           SELECT e FROM CmEmployee e
           JOIN CmEmployeeEntityAssignment a ON a.employee = e
           WHERE e.employeeCode   = :code
             AND a.legalEntity.id = :entityId
             AND a.assignmentStatus = 'ACTIVE'
           """)
    Optional<CmEmployee> findByEmployeeCodeAndLegalEntityId(
            @Param("code")     String code,
            @Param("entityId") UUID   entityId);

    /** Find all direct reports for a manager (payroll approval hierarchy). */
    List<CmEmployee> findByManagerId(UUID managerId);

    /** Batch-resolve display names for a set of authUserIds — used for custody history "approved by". */
    List<CmEmployee> findAllByAuthUserIdInAndOrganizationId(Collection<UUID> authUserIds, UUID organizationId);
}
