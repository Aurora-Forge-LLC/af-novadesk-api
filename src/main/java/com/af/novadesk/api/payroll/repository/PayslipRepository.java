package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.payroll.entity.Payslip;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID>,
        JpaSpecificationExecutor<Payslip> {

    List<Payslip> findByPayrollBatchId(UUID payrollBatchId);

    List<Payslip> findByEmployeeIdOrderByPayPeriodStartDesc(UUID employeeId);

    Optional<Payslip> findByPayrollBatchIdAndEmployeeId(UUID payrollBatchId, UUID employeeId);

    /**
     * Phase 4: legalEntityId removed from Payslip — scoped by organizationId instead.
     * Callers that previously filtered by legalEntityId should filter by payrollBatch.legalEntity.
     */
    @Query("""
           SELECT p FROM Payslip p
           WHERE p.organizationId = :organizationId
             AND p.payPeriodStart BETWEEN :from AND :to
           """)
    List<Payslip> findByOrganizationIdAndPayPeriodStartBetween(
            @Param("organizationId") UUID organizationId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Find payslips whose payroll batch belongs to the given legal entity.
     * Used for entity-scoped payslip listing (admin/HR dashboard).
     */
    @Query("""
           SELECT p FROM Payslip p
           WHERE p.payrollBatch.legalEntity.id = :legalEntityId
           ORDER BY p.payPeriodStart DESC
           """)
    List<Payslip> findByLegalEntityId(@Param("legalEntityId") UUID legalEntityId);

    /**
     * Find payslips for a specific employee within a specific legal entity.
     * Combines both filters — useful when both employeeId and legalEntityId are provided.
     */
    @Query("""
           SELECT p FROM Payslip p
           WHERE p.payrollBatch.legalEntity.id = :legalEntityId
             AND p.employee.id = :employeeId
           ORDER BY p.payPeriodStart DESC
           """)
    List<Payslip> findByLegalEntityIdAndEmployeeId(
            @Param("legalEntityId") UUID legalEntityId,
            @Param("employeeId") UUID employeeId);

    static Specification<Payslip> filterSpec(
            UUID orgId,
            UUID employeeId,
            UUID batchId,
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isDownloaded,
            String q,
            UUID legalEntityId) {

        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("organizationId"), orgId));
            SpecUtils.addIfPresent(p, employeeId,    () -> cb.equal(root.get("employee").get("id"), employeeId));
            SpecUtils.addIfPresent(p, batchId,       () -> cb.equal(root.get("payrollBatch").get("id"), batchId));
            SpecUtils.addIfPresent(p, fromDate,      () -> cb.greaterThanOrEqualTo(root.get("payPeriodStart"), fromDate));
            SpecUtils.addIfPresent(p, toDate,        () -> cb.lessThanOrEqualTo(root.get("payPeriodStart"), toDate));
            SpecUtils.addIfPresent(p, isDownloaded,  () -> cb.equal(root.get("isDownloaded"), isDownloaded));
            if (q != null && !q.isBlank()) {
                Join<Object, Object> emp = root.join("employee", JoinType.LEFT);
                p.add(SpecUtils.likeLower(cb, emp, "displayName", q));
            }
            SpecUtils.addIfPresent(p, legalEntityId, () -> cb.equal(
                    root.get("payrollBatch").get("legalEntity").get("id"), legalEntityId));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    static Specification<Payslip> filterSpec(
            UUID orgId, UUID employeeId, UUID batchId,
            LocalDate fromDate, LocalDate toDate, Boolean isDownloaded) {
        return filterSpec(orgId, employeeId, batchId, fromDate, toDate, isDownloaded, null, null);
    }
}
