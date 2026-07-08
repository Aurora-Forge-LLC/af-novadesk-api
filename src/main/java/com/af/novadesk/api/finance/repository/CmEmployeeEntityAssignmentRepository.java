package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmEmployeeEntityAssignmentRepository extends JpaRepository<CmEmployeeEntityAssignment, UUID> {

    List<CmEmployeeEntityAssignment> findAllByEmployeeId(UUID employeeId);

    /** All assignments for an employee identified by their AuthHub user ID. */
    List<CmEmployeeEntityAssignment> findAllByEmployeeAuthUserId(UUID authUserId);

    /**
     * Checks if an employee (identified by their AuthHub user ID) has an ACTIVE
     * assignment to the given legal entity. Used by {@code EntityAccessGuard}
     * as a fallback: employees implicitly have entity access via their assignment,
     * without needing a separate {@code EntityUserAccess} grant.
     */
    boolean existsByEmployeeAuthUserIdAndLegalEntityIdAndAssignmentStatus(
            UUID authUserId, UUID legalEntityId, EmployeeAssignmentStatus status);

    /** Count of assignments referencing a given department — used for delete validation. */
    long countByDepartmentId(UUID departmentId);

    Optional<CmEmployeeEntityAssignment> findByEmployeeIdAndLegalEntityId(
            UUID employeeId, UUID legalEntityId);

    /** Primary entity assignment for an employee (payroll entity). */
    Optional<CmEmployeeEntityAssignment> findByEmployeeIdAndPrimaryEntityTrue(UUID employeeId);

    boolean existsByEmployeeIdAndLegalEntityId(UUID employeeId, UUID legalEntityId);

    /** Batch lookup: all assignments for a set of employees within a given legal entity. */
    List<CmEmployeeEntityAssignment> findByEmployeeIdInAndLegalEntityId(
            List<UUID> employeeIds, UUID legalEntityId);

    /**
     * Finds assignments whose tenure [hireDate, terminationDate] overlaps
     * with the given date range. Used by payroll to include employees who
     * were hired, terminated, or transferred mid-period.
     *
     * <p>An assignment is included if:
     * <ul>
     *   <li>hireDate <= periodEnd AND</li>
     *   <li>(terminationDate IS NULL OR terminationDate >= periodStart)</li>
     * </ul>
     *
     * <p>Unlike {@link #findByEmployeeIdInAndLegalEntityId}, this query does
     * not filter by {@code status} so it captures terminated/transferred employees
     * whose assignment tenure partially overlaps the pay period.
     *
     * @param entityId     the legal entity to scope by
     * @param periodStart  start of the pay period (inclusive)
     * @param periodEnd    end of the pay period (inclusive)
     * @return assignments whose effective period overlaps the given date range
     */
    @Query("""
           SELECT a FROM CmEmployeeEntityAssignment a
           JOIN FETCH a.employee e
           WHERE a.legalEntity.id = :entityId
             AND a.hireDate <= :periodEnd
             AND (a.terminationDate IS NULL OR a.terminationDate >= :periodStart)
           """)
    List<CmEmployeeEntityAssignment> findAssignmentsOverlappingPeriod(
            @Param("entityId") UUID entityId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}
