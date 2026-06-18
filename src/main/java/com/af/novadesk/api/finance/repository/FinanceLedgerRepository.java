package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.LedgerEntrySide;
import com.af.novadesk.api.common.entity.LedgerEntry;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.dto.AggregateSum;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
 * Finance module view of the unified {@link LedgerEntry} table.
 * Covers expense, capital-injection, and bank-categorization entries.
 */
@Repository
public interface FinanceLedgerRepository extends JpaRepository<LedgerEntry, UUID>,
        JpaSpecificationExecutor<LedgerEntry> {

    List<LedgerEntry> findByReferenceTypeAndReferenceIdOrderByCreatedAtAsc(
            String referenceType, UUID referenceId);

    List<LedgerEntry> findByJournalId(UUID journalId);

    /**
     * Dynamic filter spec for the single-entity ledger report.
     * Filters by account code string instead of a UUID FK (snapshot model).
     */
    static Specification<LedgerEntry> filterSpec(
            UUID entityId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String accountCode) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("legalEntity").get("id"), entityId));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }
            if (accountCode != null && !accountCode.isBlank()) {
                predicates.add(cb.equal(root.get("accountCode"), accountCode));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    Page<LedgerEntry> findAll(Specification<LedgerEntry> spec, Pageable pageable);

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

    @Query("SELECT NEW com.af.novadesk.api.finance.dto.AggregateSum(" +
           "  COALESCE(SUM(le.amountLocal), 0), COALESCE(SUM(le.amountUsd), 0)) " +
           "  FROM LedgerEntry le " +
           " WHERE le.legalEntity = :entity " +
           "   AND le.referenceType = :referenceType" +
           "   AND le.entrySide = :debitSide")
    AggregateSum sumByEntityAndReferenceType(
            @Param("entity")        LegalEntity entity,
            @Param("referenceType") String referenceType,
            @Param("debitSide")     LedgerEntrySide debitSide);
}
