package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.specification.SpecUtils;
import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.common.entity.LegalEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CapitalInjection} (fa_capital_injections).
 */
@Repository
public interface CapitalInjectionRepository extends JpaRepository<CapitalInjection, UUID>,
        JpaSpecificationExecutor<CapitalInjection> {

    static Specification<CapitalInjection> filterSpec(
            UUID orgId, String entityCode,
            CapitalInjectionStatus injectionStatus,
            String q, FundingSource fundingSource,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String currencyLocal) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("targetEntity").get("organizationId"), orgId));
            SpecUtils.addLikeIfPresent(p, entityCode, () -> cb.equal(root.get("targetEntity").get("entityCode"), entityCode.trim().toUpperCase()));
            SpecUtils.addIfPresent(p, injectionStatus, () -> cb.equal(root.get("injectionStatus"), injectionStatus));
            SpecUtils.addLikeIfPresent(p, q,           () -> SpecUtils.likeLower(cb, root, "referenceNumber", q));
            SpecUtils.addIfPresent(p, fundingSource,   () -> cb.equal(root.get("fundingSource"), fundingSource));
            SpecUtils.addIfPresent(p, fromDate,        () -> cb.greaterThanOrEqualTo(root.get("fundingDate"), fromDate));
            SpecUtils.addIfPresent(p, toDate,          () -> cb.lessThanOrEqualTo(root.get("fundingDate"), toDate));
            SpecUtils.addIfPresent(p, minAmount,       () -> cb.greaterThanOrEqualTo(root.get("amountLocal"), minAmount));
            SpecUtils.addIfPresent(p, maxAmount,       () -> cb.lessThanOrEqualTo(root.get("amountLocal"), maxAmount));
            SpecUtils.addLikeIfPresent(p, currencyLocal, () -> cb.equal(root.get("currencyLocal"), currencyLocal.trim().toUpperCase()));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    static Specification<CapitalInjection> filterSpec(
            UUID orgId, String entityCode,
            CapitalInjectionStatus injectionStatus,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String currencyLocal) {
        return filterSpec(orgId, entityCode, injectionStatus, null, null,
                fromDate, toDate, minAmount, maxAmount, currencyLocal);
    }

    /**
     * Returns a page of capital injections for the given target entity,
     * ordered by funding date descending.
     */
    Page<CapitalInjection> findByTargetEntityOrderByFundingDateDesc(
            LegalEntity targetEntity, Pageable pageable);

    /**
     * Finds a capital injection with the target entity eagerly fetched.
     */
    @EntityGraph(attributePaths = {"targetEntity", "sourceEntity", "sourceAccount", "destinationAccount"})
    Optional<CapitalInjection> findWithRelationsById(UUID id);

    /**
     * Finds all capital injections sharing a transfer ID (inter-entity transfer lookup).
     */
    @EntityGraph(attributePaths = {"targetEntity", "sourceEntity"})
    List<CapitalInjection> findByTransferId(UUID transferId);

    /**
     * Sums capital injection amounts (local and USD) for a given target entity,
     * considering only POSTED injections.
     *
     * @return type-safe {@link AggregateSum} with COALESCE'd non-null amounts
     */
    @Query("SELECT NEW com.af.novadesk.api.finance.dto.AggregateSum(" +
           "  COALESCE(SUM(ci.amountLocal), 0), COALESCE(SUM(ci.amountUsd), 0)) " +
           "  FROM CapitalInjection ci " +
           " WHERE ci.targetEntity = :entity " +
           "   AND ci.injectionStatus = 'POSTED'")
    com.af.novadesk.api.finance.dto.AggregateSum sumByTargetEntity(
            @Param("entity") com.af.novadesk.api.common.entity.LegalEntity entity);
}


