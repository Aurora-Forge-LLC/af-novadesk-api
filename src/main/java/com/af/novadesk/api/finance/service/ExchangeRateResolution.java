package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.RateSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable result of an exchange-rate resolution (LLR-FIN-02.3 / LLR-FIN-04.4).
 *
 * @param rate       The resolved multiplier to convert source currency → reporting currency.
 *                   Must be non-null; the service always calls {@link Objects#requireNonNull}
 *                   after resolution, and this compact constructor provides the same guard at
 *                   the record level so callers cannot construct an invalid instance.
 * @param rateSource How the rate was obtained (API, LOOKBACK, MANUAL, or IDENTITY).
 * @param rateDate   The date to which this rate applies (may differ from the transaction
 *                   date when a look-back rate is used).
 * @param warning    True when a LOOKBACK rate was used (nearest past rate within the
 *                   configured window) — flags the ledger entry for review (LLR-FIN-04.4).
 */
public record ExchangeRateResolution(
        BigDecimal rate,
        RateSource rateSource,
        LocalDate rateDate,
        boolean warning
) {
    /** Compact constructor — validates that no field is null. */
    public ExchangeRateResolution {
        Objects.requireNonNull(rate,       "ExchangeRateResolution.rate must not be null");
        Objects.requireNonNull(rateSource, "ExchangeRateResolution.rateSource must not be null");
        Objects.requireNonNull(rateDate,   "ExchangeRateResolution.rateDate must not be null");
    }
}
