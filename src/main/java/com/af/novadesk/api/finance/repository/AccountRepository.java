package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.entity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Account} (fa_accounts).
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Returns all accounts with the legalEntity association eagerly fetched
     * in a single JOIN query — avoids N+1 SELECT when mapping accounts to DTOs.
     */
    @EntityGraph(attributePaths = "legalEntity")
    @Query("SELECT a FROM Account a")
    List<Account> findAllWithLegalEntity();

    /**
     * Returns a single account with legalEntity eagerly fetched.
     * Prevents lazy-loading failures when mapping to DTO outside a persistence context.
     */
    @EntityGraph(attributePaths = "legalEntity")
    Optional<Account> findWithLegalEntityById(UUID id);

    /**
     * Finds the first active account with the given role for a legal entity.
     * Used by {@code CapitalInjectionService} to resolve default source/destination
     * accounts without requiring the caller to know the account ID.
     */
    Optional<Account> findFirstByLegalEntityAndAccountRoleAndStatus(
            LegalEntity legalEntity,
            AccountRole accountRole,
            Status status
    );

    /**
     * Returns all accounts (with legalEntity eagerly fetched) for a given
     * legal entity, identified by its UUID.
     * Used by {@code AccountService.listByEntityId(UUID)}.
     */
    @EntityGraph(attributePaths = "legalEntity")
    @Query("SELECT a FROM Account a WHERE a.legalEntity.id = :legalEntityId")
    List<Account> findAllByLegalEntityId(UUID legalEntityId);
}
