package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.StatementStatus;
import com.af.novadesk.api.finance.entity.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link BankStatement} entity (LLR-BNK-01).
 */
@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

    /**
     * Find all statements for a given legal entity (org-scoped).
     */
    @Query("""
           SELECT bs FROM BankStatement bs
           WHERE bs.legalEntity.id = :legalEntityId
             AND bs.status = 'ACTIVE'
           ORDER BY bs.createdAt DESC
           """)
    Page<BankStatement> findAllByLegalEntityId(
            @Param("legalEntityId") UUID legalEntityId,
            Pageable pageable);

    /**
     * Find a statement by ID with its relationships eagerly loaded for detail view.
     */
    @Query("""
           SELECT bs FROM BankStatement bs
           JOIN FETCH bs.legalEntity
           JOIN FETCH bs.bankAccount
           JOIN FETCH bs.uploadedBy
           WHERE bs.id = :id
             AND bs.status = 'ACTIVE'
           """)
    Optional<BankStatement> findWithRelationsById(@Param("id") UUID id);

    /**
     * Check for duplicate statements — same bank account, overlapping period, not superseded.
     */
    @Query("""
           SELECT bs FROM BankStatement bs
           WHERE bs.bankAccount.id = :bankAccountId
             AND bs.periodStart = :periodStart
             AND bs.periodEnd = :periodEnd
             AND bs.statementStatus != 'SUPERSEDED'
             AND bs.status = 'ACTIVE'
           """)
    List<BankStatement> findActiveByBankAccountAndPeriod(
            @Param("bankAccountId") UUID bankAccountId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Check if a statement with the given ID exists and is not superseded.
     */
    boolean existsByIdAndStatementStatusNot(UUID id, StatementStatus statementStatus);
}
