package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Account} (fa_accounts).
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

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
}

