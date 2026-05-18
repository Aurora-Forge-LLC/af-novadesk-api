package com.af.novadesk.api.finance.funding.repository;

import com.af.novadesk.api.finance.funding.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link LedgerEntry} (fa_ledger_entries).
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
}

