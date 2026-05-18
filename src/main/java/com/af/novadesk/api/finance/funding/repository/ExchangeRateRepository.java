package com.af.novadesk.api.finance.funding.repository;

import com.af.novadesk.api.finance.funding.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ExchangeRate} (fa_exchange_rates).
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    /**
     * Returns an exact-date rate for the given currency pair, if one exists.
     */
    Optional<ExchangeRate> findBySourceCurrencyAndTargetCurrencyAndRateDate(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate
    );

    /**
     * Returns the most recent rate for the given currency pair whose date falls
     * within {@code [minDate, rateDate]}.  Used for look-back resolution when no
     * exact rate exists for the transaction date (LLR-FIN-02.3).
     */
    @Query("""
            SELECT er FROM ExchangeRate er
            WHERE er.sourceCurrency = :sourceCurrency
              AND er.targetCurrency = :targetCurrency
              AND er.rateDate       <= :rateDate
              AND er.rateDate       >= :minDate
            ORDER BY er.rateDate DESC
            """)
    Optional<ExchangeRate> findNearestPastRateWithinWindow(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            LocalDate minDate
    );
}

