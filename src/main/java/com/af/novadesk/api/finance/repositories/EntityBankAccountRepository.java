package com.af.novadesk.api.finance.repositories;

import com.af.novadesk.api.finance.constants.BankAccountType;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link EntityBankAccount} entity.
 */
@Repository
public interface EntityBankAccountRepository extends JpaRepository<EntityBankAccount, UUID> {

    List<EntityBankAccount> findAllByLegalEntityId(UUID legalEntityId);

    Optional<EntityBankAccount> findByLegalEntityIdAndAccountType(
            UUID legalEntityId, BankAccountType accountType);

    boolean existsByLegalEntityIdAndAccountType(UUID legalEntityId, BankAccountType accountType);
}