package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ExchangeRate} (fa_exchange_rates).
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID>,
        JpaSpecificationExecutor<ExchangeRate> {

    /**
     * Returns an exact-date, active rate for the given currency pair, if one exists.
     */
    Optional<ExchangeRate> findBySourceCurrencyAndTargetCurrencyAndRateDateAndStatus(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            Status status
    );

    /**
     * Builds a dynamic {@link Specification} that honours every combination of
     * the three optional filters.  {@code NULL} parameters are omitted from the
     * WHERE clause entirely, avoiding the "could not determine data type of
     * parameter" PostgreSQL error that occurs when Hibernate passes a bare
     * {@code NULL} for a {@link LocalDate} parameter in a JPQL
     * {@code :param IS NULL OR field = :param} pattern.
     *
     * <p>Results are always ordered by {@code rateDate DESC}.</p>
     */
    static Specification<ExchangeRate> filterSpec(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (sourceCurrency != null) {
                predicates.add(cb.equal(root.get("sourceCurrency"), sourceCurrency));
            }
            if (targetCurrency != null) {
                predicates.add(cb.equal(root.get("targetCurrency"), targetCurrency));
            }
            if (rateDate != null) {
                predicates.add(cb.equal(root.get("rateDate"), rateDate));
            }

            query.orderBy(cb.desc(root.get("rateDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Returns the single most recent active rate for the given currency pair
     * whose date falls within {@code [minDate, rateDate]}.  Used for look-back
     * resolution when no exact rate exists for the transaction date (LLR-FIN-02.3).
     *
     * <p>Fix: Added {@code AND er.status = 'ACTIVE'} to exclude soft-deleted rates
     * and {@code LIMIT 1} to prevent {@code NonUniqueResultException} when multiple
     * rates exist within the look-back window.</p>
     */
    @Query("""
            SELECT er FROM ExchangeRate er
            WHERE er.sourceCurrency = :sourceCurrency
              AND er.targetCurrency = :targetCurrency
              AND er.rateDate       <= :rateDate
              AND er.rateDate       >= :minDate
              AND er.status         = 'ACTIVE'
            ORDER BY er.rateDate DESC
            LIMIT 1
            """)
    Optional<ExchangeRate> findNearestPastRateWithinWindow(
            @Param("sourceCurrency") String sourceCurrency,
            @Param("targetCurrency") String targetCurrency,
            @Param("rateDate") LocalDate rateDate,
            @Param("minDate") LocalDate minDate);
}
