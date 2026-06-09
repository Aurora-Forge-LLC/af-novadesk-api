package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /** Phase 5: find by linked cm_employees id (replaces findByLegalEntityId). */
    Optional<Employee> findByCmEmployeeId(UUID cmEmployeeId);

    List<Employee> findByCmEmployeeIdIn(List<UUID> cmEmployeeIds);

    /** Manager hierarchy — still valid since manager_id is within pr_employees. */
    List<Employee> findByManagerId(UUID managerId);

    /**
     * Find all employees for a legal entity via cm_employee_entity_assignments.
     * Replaces the old findByLegalEntityId after legal_entity_id was dropped in V1.74.
     */
    @Query("""
           SELECT e FROM Employee e
           WHERE e.cmEmployeeId IN (
               SELECT a.employee.id FROM CmEmployeeEntityAssignment a
               WHERE a.legalEntity.id  = :entityId
                 AND a.assignmentStatus = 'ACTIVE'
           )
           """)
    List<Employee> findByLegalEntityId(@Param("entityId") UUID entityId);

    /**
     * Legacy method kept for backward compatibility — no longer functional after
     * auth_user_id was dropped from pr_employees in V1.74. Use CmEmployeeRepository
     * to look up by auth_user_id instead.
     *
     * @deprecated Use {@link com.af.novadesk.api.common.repository.CmEmployeeRepository}
     */
    @Deprecated
    default Optional<Employee> findByAuthUserIdAndLegalEntityId(UUID authUserId, UUID legalEntityId) {
        return Optional.empty();
    }
}
