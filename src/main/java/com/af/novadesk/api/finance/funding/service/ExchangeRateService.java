package com.af.novadesk.api.finance.funding.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resolves the exchange rate to use for USD conversion during capital injection
 * (LLR-FIN-02.3).
 *
 * <p>Implementations must support:</p>
 * <ol>
 *   <li>Same-currency identity (rate = 1).</li>
 *   <li>Manual rate supplied by the caller (requires justification + approver).</li>
 *   <li>Exact-date lookup from the internal rate table.</li>
 *   <li>Look-back lookup — most recent rate within the configured window.</li>
 *   <li>Throwing {@code MissingExchangeRateException} when no rate can be found.</li>
 * </ol>
 */
public interface ExchangeRateService {

    /**
     * Resolves the exchange rate for the given currency pair on the given date.
     *
     * @param sourceCurrency         ISO 4217 source currency code.
     * @param targetCurrency         ISO 4217 target (reporting) currency code.
     * @param transactionDate        Date for which the rate is required.
     * @param manualRate             Optional manual rate supplied by the user; if
     *                               non-null it takes precedence over table lookup.
     * @param manualRateJustification Required when {@code manualRate} is provided.
     * @param manualRateApprovedBy    Required when {@code manualRate} is provided.
     * @return resolved rate, source label, and effective date.
     */
    ExchangeRateResolution resolveRate(
            String sourceCurrency,
            String targetCurrency,
            LocalDate transactionDate,
            BigDecimal manualRate,
            String manualRateJustification,
            String manualRateApprovedBy
    );
}

