package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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
     * Flexible list query that honours every combination of the three optional
     * filters.  {@code NULL} parameters are treated as "no filter" via the
     * {@code :param IS NULL OR field = :param} JPQL pattern, eliminating the
     * previous fallback-to-findAll() behaviour that silently ignored partial
     * inputs (M1).
     */
    @Query("""
            SELECT er FROM ExchangeRate er
             WHERE (:sourceCurrency IS NULL OR er.sourceCurrency = :sourceCurrency)
               AND (:targetCurrency IS NULL OR er.targetCurrency = :targetCurrency)
               AND (:rateDate       IS NULL OR er.rateDate       = :rateDate)
             ORDER BY er.rateDate DESC
            """)
    List<ExchangeRate> findByFilters(
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("rateDate") LocalDate rateDate);

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
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("rateDate") LocalDate rateDate,
            @Param("minDate") LocalDate minDate);
}
