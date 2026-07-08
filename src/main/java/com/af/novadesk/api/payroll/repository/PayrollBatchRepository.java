package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import com.af.novadesk.api.payroll.entity.PayrollBatch;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, UUID>,
        JpaSpecificationExecutor<PayrollBatch> {

    List<PayrollBatch> findAllByOrderByCreatedAtDesc();
    List<PayrollBatch> findByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId);
    Optional<PayrollBatch> findByLegalEntityIdAndPayPeriodStartAndPayPeriodEndAndStatus(
            UUID legalEntityId, LocalDate periodStart, LocalDate periodEnd, Status status);
    List<PayrollBatch> findByBatchStatus(PayrollBatchStatus status);

    static Specification<PayrollBatch> filterSpec(
            UUID orgId,
            UUID legalEntityId,
            PayrollBatchStatus batchStatus,
            String currencyCode,
            LocalDate payPeriodFrom,
            LocalDate payPeriodTo,
            LocalDate paymentDateFrom,
            LocalDate paymentDateTo) {

        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("legalEntity").get("organizationId"), orgId));
            SpecUtils.addIfPresent(p, legalEntityId,    () -> cb.equal(root.get("legalEntity").get("id"), legalEntityId));
            SpecUtils.addIfPresent(p, batchStatus,      () -> cb.equal(root.get("batchStatus"), batchStatus));
            SpecUtils.addLikeIfPresent(p, currencyCode, () -> cb.equal(root.get("currencyCode"),
                    currencyCode != null ? currencyCode.trim().toUpperCase() : null));
            SpecUtils.addIfPresent(p, payPeriodFrom,    () -> cb.greaterThanOrEqualTo(root.get("payPeriodStart"), payPeriodFrom));
            SpecUtils.addIfPresent(p, payPeriodTo,      () -> cb.lessThanOrEqualTo(root.get("payPeriodEnd"), payPeriodTo));
            SpecUtils.addIfPresent(p, paymentDateFrom,  () -> cb.greaterThanOrEqualTo(root.get("paymentDate"), paymentDateFrom));
            SpecUtils.addIfPresent(p, paymentDateTo,    () -> cb.lessThanOrEqualTo(root.get("paymentDate"), paymentDateTo));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
