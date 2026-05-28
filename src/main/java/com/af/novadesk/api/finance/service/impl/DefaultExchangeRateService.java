package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.service.ExchangeRateResolution;
import com.af.novadesk.api.finance.service.ExchangeRateService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Default implementation of {@link ExchangeRateService}.
 *
 * <p>Resolution order (LLR-FIN-02.3):</p>
 * <ol>
 *   <li>Identity: if source == target return 1:1.</li>
 *   <li>Manual: caller-supplied rate (validated; requires justification + approver).</li>
 *   <li>Exact-date table lookup.</li>
 *   <li>Look-back: nearest rate within {@link FundingProperties#exchangeRateLookbackDays()} days.</li>
 *   <li>{@link MissingExchangeRateException} — prompts user to enter a manual rate.</li>
 * </ol>
 */
@Service
public class DefaultExchangeRateService implements ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final FundingProperties fundingProperties;

    public DefaultExchangeRateService(
            ExchangeRateRepository exchangeRateRepository,
            FundingProperties fundingProperties
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.fundingProperties = fundingProperties;
    }

    @Override
    public ExchangeRateResolution resolveRate(
            String sourceCurrency,
            String targetCurrency,
            LocalDate transactionDate,
            BigDecimal manualRate,
            String manualRateJustification,
            String manualRateApprovedBy
    ) {
        String src = normalize(sourceCurrency);
        String tgt = normalize(targetCurrency);

        // 1. Same-currency — no conversion needed
        if (src.equals(tgt)) {
            return new ExchangeRateResolution(BigDecimal.ONE, RateSource.IDENTITY, transactionDate, false);
        }

        // 2. Caller-supplied manual rate (takes precedence over table lookup)
        if (manualRate != null) {
            validateManualRate(manualRate, manualRateJustification, manualRateApprovedBy);
            return new ExchangeRateResolution(manualRate, RateSource.MANUAL, transactionDate, false);
        }

        // 3. Exact-date table lookup (only ACTIVE rates)
        ExchangeRate exact = exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrencyAndRateDateAndStatus(src, tgt, transactionDate, com.af.novadesk.api.common.constants.Status.ACTIVE)
                .orElse(null);
        if (exact != null) {
            return new ExchangeRateResolution(exact.getExchangeRate(), exact.getRateSource(), exact.getRateDate(), false);
        }

        // 4. Look-back within configured window — sets warning flag (LLR-FIN-04.4)
        LocalDate minDate = transactionDate.minusDays(fundingProperties.exchangeRateLookbackDays());
        ExchangeRate nearest = exchangeRateRepository
                .findNearestPastRateWithinWindow(src, tgt, transactionDate, minDate)
                .orElse(null);
        if (nearest != null) {
            return new ExchangeRateResolution(nearest.getExchangeRate(), RateSource.LOOKBACK, nearest.getRateDate(), true);
        }

        // 5. No rate available — caller must supply a manual rate
        throw new MissingExchangeRateException(
                "No exchange rate found for " + src + " → " + tgt + " on " + transactionDate
                        + ". Please provide a manual rate with justification and approver."
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void validateManualRate(
            BigDecimal manualRate,
            String justification,
            String approvedBy
    ) {
        if (manualRate.signum() <= 0) {
            throw new BadRequestException("Manual exchange rate must be greater than zero");
        }
        // M6: upper-bound sanity check — catches data-entry typos (e.g. 12000 vs 0.012)
        BigDecimal ceiling = fundingProperties.maxManualExchangeRate();
        if (manualRate.compareTo(ceiling) > 0) {
            throw new BadRequestException(
                    "Manual exchange rate " + manualRate + " exceeds the allowed ceiling of "
                    + ceiling + ". If this rate is intentional, update the system configuration.");
        }
        if (justification == null || justification.isBlank()) {
            throw new BadRequestException("A justification note is required when supplying a manual exchange rate");
        }
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new BadRequestException("An approver name is required when supplying a manual exchange rate");
        }
    }

    private String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Currency code must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}


