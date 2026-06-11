package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.LedgerEntry;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link LedgerEntry} (fa_ledger_entries).
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>,
        JpaSpecificationExecutor<LedgerEntry> {

    /**
     * Finds all ledger entries for a given reference type and reference ID.
     * Eagerly fetches the account for display in detail views.
     */
    @EntityGraph(attributePaths = {"account", "legalEntity"})
    List<LedgerEntry> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
            String referenceType, UUID referenceId);

    /**
     * Finds all ledger entries sharing a journal ID.
     */
    List<LedgerEntry> findByJournalId(UUID journalId);

    /**
     * Builds a dynamic {@link Specification} for the single-entity ledger report
     * that honours every combination of the optional filters.  {@code NULL}
     * parameters are omitted from the WHERE clause entirely, avoiding the
     * "could not determine data type of parameter" PostgreSQL error that occurs
     * when Hibernate passes a bare {@code NULL} for a {@link LocalDateTime} or
     * {@link UUID} parameter in a JPQL {@code :param IS NULL OR field = :param}
     * pattern.
     *
     * <p>Results are always ordered by {@code createdAt DESC}.</p>
     */
    static Specification<LedgerEntry> filterSpec(
            UUID entityId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID accountId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("legalEntity").get("id"), entityId));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Paginated query for the single-entity ledger report (LLR-FIN-04.5).
     * Uses a dynamic {@link Specification} to avoid nullable-parameter type
     * inference errors in PostgreSQL.
     */
    @EntityGraph(attributePaths = {"account", "chartOfAccount"})
    Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);

    /**
     * Aggregation query for the multi-entity consolidated report (LLR-FIN-04.5).
     * Groups ledger entries by entity and side, summing both USD and local amounts.
     *
     * <p>Date filtering is optional — pass a wide date range (e.g. 1970–2099)
     * instead of {@code NULL} to include all entries.</p>
     */
    @Query("""
        SELECT le.legalEntity.id,
               le.entrySide,
               SUM(le.amountUsd),
               SUM(le.amountLocal)
          FROM LedgerEntry le
         WHERE le.legalEntity.id IN :entityIds
           AND le.createdAt >= :startDate
           AND le.createdAt <= :endDate
         GROUP BY le.legalEntity.id, le.entrySide
        """)
    List<Object[]> aggregateByEntity(
            @Param("entityIds") List<UUID> entityIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate);

    /**
     * Sums debit amounts (local and USD) for a given entity and
     * reference type.  Used by the entity-balance endpoint to compute expense
     * USD totals (capital-injection entries use the CapitalInjection table
     * directly which already stores both local and USD amounts).
     *
     * @return type-safe {@link AggregateSum} with COALESCE'd non-null amounts
     */
    @Query("SELECT NEW com.af.novadesk.api.finance.dto.AggregateSum(" +
           "  COALESCE(SUM(le.amountLocal), 0), COALESCE(SUM(le.amountUsd), 0)) " +
           "  FROM LedgerEntry le " +
           " WHERE le.legalEntity = :entity " +
           "   AND le.referenceType = :referenceType" +
           "   AND le.entrySide = 'DEBIT'")
    com.af.novadesk.api.finance.dto.AggregateSum sumByEntityAndReferenceType(
            @Param("entity") com.af.novadesk.api.common.entity.LegalEntity entity,
            @Param("referenceType") String referenceType);
}
