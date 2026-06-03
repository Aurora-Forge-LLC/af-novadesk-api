package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.LegalEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CapitalInjection} (fa_capital_injections).
 */
@Repository
public interface CapitalInjectionRepository extends JpaRepository<CapitalInjection, UUID> {

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
            @Param("entity") com.af.novadesk.api.finance.entity.LegalEntity entity);
}


