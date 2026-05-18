package com.af.novadesk.api.finance.funding.repository;

import com.af.novadesk.api.finance.funding.entity.CapitalInjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for {@link CapitalInjection} (fa_capital_injections).
 */
@Repository
public interface CapitalInjectionRepository extends JpaRepository<CapitalInjection, UUID> {
}

