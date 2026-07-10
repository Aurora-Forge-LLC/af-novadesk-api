package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.specification.SpecUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CmEmployeeRepository extends JpaRepository<CmEmployee, UUID>,
        JpaSpecificationExecutor<CmEmployee> {

    static Specification<CmEmployee> filterSpec(
            UUID orgId, String q, EmployeeStatus status,
            UUID legalEntityId, UUID managerId) {
        return filterSpec(orgId, q, status, legalEntityId, managerId, null);
    }

    static Specification<CmEmployee> filterSpec(
            UUID orgId, String q, EmployeeStatus status,
            UUID legalEntityId, UUID managerId, java.util.List<UUID> employeeIdIn) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));

            SpecUtils.addLikeIfPresent(p, q, () -> cb.or(
                    SpecUtils.likeLower(cb, root, "displayName", q),
                    SpecUtils.likeLower(cb, root, "email", q),
                    SpecUtils.likeLower(cb, root, "employeeCode", q)
            ));
            SpecUtils.addIfPresent(p, status,    () -> cb.equal(root.get("employeeStatus"), status));
            SpecUtils.addIfPresent(p, managerId, () -> cb.equal(root.get("manager").get("id"), managerId));
            if (employeeIdIn != null && !employeeIdIn.isEmpty()) {
                p.add(root.get("id").in(employeeIdIn));
            }

            if (legalEntityId != null) {
                var sub = query.subquery(UUID.class);
                var asgn = sub.from(CmEmployeeEntityAssignment.class);
                sub.select(asgn.get("employee").get("id"))
                   .where(cb.and(
                           cb.equal(asgn.get("legalEntity").get("id"), legalEntityId),
                           cb.equal(asgn.get("assignmentStatus"), EmployeeAssignmentStatus.ACTIVE)
                   ));
                p.add(root.get("id").in(sub));
            }

            query.distinct(true);
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

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

    /** IDs of active direct reports for a given manager — used by CallerContext for data scoping. */
    @Query("SELECT e.id FROM CmEmployee e WHERE e.manager.id = :managerId AND e.employeeStatus = 'ACTIVE'")
    List<UUID> findActiveIdsByManagerId(@Param("managerId") UUID managerId);

    /** Batch-resolve display names for a set of authUserIds — used for custody history "approved by". */
    List<CmEmployee> findAllByAuthUserIdInAndOrganizationId(Collection<UUID> authUserIds, UUID organizationId);
}
