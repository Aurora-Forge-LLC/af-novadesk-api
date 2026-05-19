package com.af.novadesk.api.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Externalised configuration for the capital-funding sub-domain.
 *
 * <p>Bound from the {@code finance.funding} prefix in application YAML via
 * Spring Boot 3.x canonical-constructor binding (records are bound automatically
 * without {@code @ConstructorBinding}).</p>
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * finance:
 *   funding:
 *     reporting-currency: USD
 *     exchange-rate-lookback-days: 7
 *     max-manual-exchange-rate: 10000
 * }</pre>
 *
 * @param reportingCurrency      ISO 4217 code of the group-wide reporting currency.
 *                               All ledger entries are converted to this currency.
 * @param exchangeRateLookbackDays Maximum calendar days to look back when no exact rate
 *                               exists for the transaction date (LLR-FIN-02.3).
 * @param maxManualExchangeRate  Upper bound on caller-supplied manual exchange rates.
 *                               Catches data-entry typos (e.g. 12000 instead of 0.012)
 *                               that would silently inflate USD ledger amounts (M6).
 */
@ConfigurationProperties(prefix = "finance.funding")
public record FundingProperties(
        String reportingCurrency,
        int exchangeRateLookbackDays,
        BigDecimal maxManualExchangeRate
) {
    /** Default constructor — used when no YAML overrides are provided. */
    public FundingProperties() {
        this("USD", 7, new BigDecimal("10000"));
    }
}
