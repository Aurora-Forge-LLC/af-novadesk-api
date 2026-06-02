package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.af.novadesk.api.payroll.entity.PayrollBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, UUID> {
    List<PayrollBatch> findByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId);
    Optional<PayrollBatch> findByLegalEntityIdAndPayPeriodStartAndPayPeriodEnd(
            UUID legalEntityId, LocalDate periodStart, LocalDate periodEnd);
    List<PayrollBatch> findByBatchStatus(PayrollBatchStatus status);
}
