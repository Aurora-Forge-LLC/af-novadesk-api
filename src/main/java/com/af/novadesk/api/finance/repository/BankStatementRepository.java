package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.finance.entity.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link BankStatement} entity.
 */
@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

    /**
     * Returns all statements for a given legal entity, ordered by upload date descending.
     */
    Page<BankStatement> findAllByLegalEntityIdOrderByCreatedAtDesc(UUID legalEntityId, Pageable pageable);

    /**
     * Returns all statements for a given bank account, ordered by upload date descending.
     */
    Page<BankStatement> findAllByBankAccountIdOrderByCreatedAtDesc(UUID bankAccountId, Pageable pageable);

    /**
     * LLR-BNK-01.1: Checks for an existing non-superseded statement for the given
     * bank account + period overlap. Used to detect duplicates before upload.
     */
    @Query("""
           SELECT bs FROM BankStatement bs
           WHERE bs.bankAccount.id = :bankAccountId
             AND bs.periodStart <= :periodEnd
             AND bs.periodEnd >= :periodStart
             AND bs.statementStatus <> 'SUPERSEDED'
             AND bs.status = 'ACTIVE'
           """)
    Optional<BankStatement> findOverlappingActive(
            @Param("bankAccountId") UUID bankAccountId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Supersedes all active statements for a given bank account + period overlap
     * by setting their statement_status to SUPERSEDED.
     *
     * <p>{@code clearAutomatically = true} ensures the persistence context is
     * flushed and cleared after this bulk update, so subsequent queries (e.g.
     * the duplicate check in {@code uploadStatement}) see the updated rows
     * rather than stale cached entities.</p>
     */
    @Modifying(clearAutomatically = true)
    @Query("""
           UPDATE BankStatement bs
           SET bs.statementStatus = 'SUPERSEDED'
           WHERE bs.bankAccount.id = :bankAccountId
             AND bs.periodStart <= :periodEnd
             AND bs.periodEnd >= :periodStart
             AND bs.statementStatus <> 'SUPERSEDED'
             AND bs.status = 'ACTIVE'
           """)
    void supersedeOverlappingStatements(
            @Param("bankAccountId") UUID bankAccountId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Returns a single statement by ID, scoped to a legal entity (org isolation).
     */
    Optional<BankStatement> findByIdAndLegalEntityId(UUID id, UUID legalEntityId);
}
