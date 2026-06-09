package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

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
}
