package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link ChartOfAccount} entity.
 */
@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {

    List<ChartOfAccount> findAllByLegalEntityIdOrderByAccountCodeAsc(UUID legalEntityId);

    List<ChartOfAccount> findAllByLegalEntityIdAndAccountType(
            UUID legalEntityId, AccountType accountType);

    boolean existsByLegalEntityIdAndAccountCode(UUID legalEntityId, String accountCode);

    /** Used during CoA seeding to delete all system-generated accounts before re-seeding. */
    @Modifying
    @Query("DELETE FROM ChartOfAccount c WHERE c.legalEntity.id = :entityId AND c.systemGenerated = true")
    void deleteSystemGeneratedByLegalEntityId(@Param("entityId") UUID entityId);
}
