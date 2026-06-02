package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<Payslip> findByLegalEntityIdAndPayPeriodStartBetween(
            UUID legalEntityId, LocalDate from, LocalDate to);
}
