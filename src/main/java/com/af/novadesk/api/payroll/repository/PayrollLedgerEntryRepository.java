package com.af.novadesk.api.payroll.repository;

import com.af.novadesk.api.payroll.entity.PayrollLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollLedgerEntryRepository extends JpaRepository<PayrollLedgerEntry, UUID> {
    List<PayrollLedgerEntry> findByJournalId(UUID journalId);
    List<PayrollLedgerEntry> findByPayrollBatchId(UUID payrollBatchId);
    List<PayrollLedgerEntry> findByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId);
}
