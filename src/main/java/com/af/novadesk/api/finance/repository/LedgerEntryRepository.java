package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link LedgerEntry} (fa_ledger_entries).
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

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
     * Paginated query for the single-entity ledger report (LLR-FIN-04.5).
     * Supports optional filtering by date range and account.
     */
    @EntityGraph(attributePaths = {"account"})
    @Query("""
        SELECT le FROM LedgerEntry le
         WHERE le.legalEntity.id = :entityId
           AND (:startDate IS NULL OR le.createdAt >= :startDate)
           AND (:endDate   IS NULL OR le.createdAt <= :endDate)
           AND (:accountId IS NULL OR le.account.id = :accountId)
         ORDER BY le.createdAt DESC
        """)
    Page<LedgerEntry> findForReport(
            @Param("entityId")  UUID entityId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            @Param("accountId") UUID accountId,
            Pageable pageable);

    /**
     * Aggregation query for the multi-entity consolidated report (LLR-FIN-04.5).
     * Groups ledger entries by entity and side, summing both USD and local amounts.
     */
    @Query("""
        SELECT le.legalEntity.id,
               le.entrySide,
               SUM(le.amountUsd),
               SUM(le.amountLocal)
          FROM LedgerEntry le
         WHERE le.legalEntity.id IN :entityIds
           AND (:startDate IS NULL OR le.createdAt >= :startDate)
           AND (:endDate   IS NULL OR le.createdAt <= :endDate)
         GROUP BY le.legalEntity.id, le.entrySide
        """)
    List<Object[]> aggregateByEntity(
            @Param("entityIds") List<UUID> entityIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate);
}


