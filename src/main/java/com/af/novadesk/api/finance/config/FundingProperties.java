package com.af.novadesk.api.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the capital-funding sub-domain.
 *
 * <p>Bound from the {@code finance.funding} prefix in application YAML.
 * All properties have safe defaults so the application starts in a local
 * environment without any extra configuration.</p>
 *
 * <p>Example YAML:</p>
 * <pre>{@code
 * finance:
 *   funding:
 *     reporting-currency: USD
 *     exchange-rate-lookback-days: 7
 * }</pre>
 */
@ConfigurationProperties(prefix = "finance.funding")
public class FundingProperties {

    /**
     * ISO 4217 code of the group-wide reporting currency.
     * All ledger entries are converted to this currency for cross-entity reporting.
     */
    private String reportingCurrency = "USD";

    /**
     * Maximum number of calendar days to look back when no exact exchange rate
     * exists for the transaction date (LLR-FIN-02.3).
     */
    private int exchangeRateLookbackDays = 7;

    public String getReportingCurrency() { return reportingCurrency; }
    public int getExchangeRateLookbackDays() { return exchangeRateLookbackDays; }

    public void setReportingCurrency(String reportingCurrency) {
        this.reportingCurrency = reportingCurrency;
    }
    public void setExchangeRateLookbackDays(int exchangeRateLookbackDays) {
        this.exchangeRateLookbackDays = exchangeRateLookbackDays;
    }
}

