package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.common.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Payroll module view of the unified {@link LedgerEntry} table.
 * referenceId == payrollBatch.id for all payroll entries.
 */
@Repository
public interface PayrollLedgerRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByJournalId(UUID journalId);

    /** Replaces the old findByPayrollBatchId — referenceId equals batch.id. */
    List<LedgerEntry> findByReferenceId(UUID referenceId);

    List<LedgerEntry> findByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId);
}
