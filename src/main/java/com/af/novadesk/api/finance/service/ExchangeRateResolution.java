package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.RateSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable result of an exchange-rate resolution (LLR-FIN-02.3).
 *
 * @param rate       The resolved multiplier to convert source currency → reporting currency.
 * @param rateSource How the rate was obtained (API, LOOKBACK, MANUAL, or IDENTITY).
 * @param rateDate   The date to which this rate applies (may differ from the transaction
 *                   date when a look-back rate is used).
 */
public record ExchangeRateResolution(
        BigDecimal rate,
        RateSource rateSource,
        LocalDate rateDate
) {
}

