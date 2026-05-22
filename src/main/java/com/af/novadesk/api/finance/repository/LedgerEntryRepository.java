package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.LedgerEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}


