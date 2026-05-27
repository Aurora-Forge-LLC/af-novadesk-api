package com.af.novadesk.api.finance.scheduler;

import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.service.ExchangeRateOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that fetches daily exchange rates from an external API and
 * persists them to the {@code fa_exchange_rates} table (LLR-FIN-02.3).
 *
 * <p>This scheduler follows the <strong>Transactional Outbox Pattern</strong>
 * already established by {@code CapitalInjectionOutboxService},
 * {@code LegalEntityOutboxService}, and {@code EntityUserAccessOutboxService}
 * for modular-monolith consistency.</p>
 *
 * <h2>Retry & Dead Letter Queue Strategy</h2>
 * <ol>
 *   <li><b>Attempt 1:</b> Fetch rate from external API.</li>
 *   <li><b>Attempt 2:</b> Retry after 30-second backoff.</li>
 *   <li><b>Attempt 3:</b> Retry after 2-minute backoff.</li>
 *   <li><b>DLQ:</b> All retries exhausted → publish
 *       {@code EXCHANGE_RATE_SYNC_FAILED} outbox event for operator inspection.</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * <p>Before fetching, the scheduler checks if a rate already exists for the
 * currency pair and date. If found, the pair is skipped (idempotent replay).</p>
 *
 * <h2>Configuration</h2>
 * <p>The cron expression is intentionally left empty — to be configured when
 * an external exchange rate API is integrated.</p>
 *
 * <pre>{@code
 * finance:
 *   funding:
 *     exchange-rate-sync-cron: ""  # e.g., "0 0 8 * * ?" for daily at 8 AM
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateSyncScheduler {

    private static final int MAX_RETRIES = 3;

    private final LegalEntityRepository      legalEntityRepository;
    private final ExchangeRateRepository      exchangeRateRepository;
    private final ExchangeRateOutboxService   outboxService;

    // -------------------------------------------------------------------------
    // External API client — to be injected when API integration is ready
    // -------------------------------------------------------------------------
    // private final ExchangeRateApiClient externalApiClient;

    /**
     * Scheduled daily sync of exchange rates for all active non-USD entities.
     *
     * <p>The cron expression is intentionally left empty ({@code ""}) as no
     * external exchange rate API has been integrated yet. Uncomment and
     * configure when ready.</p>
     *
     * <p>Example cron: {@code 0 0 8 * * ?} — runs every day at 8:00 AM.</p>
     */
    // @Scheduled(cron = "${finance.funding.exchange-rate-sync-cron}")
    public void syncDailyExchangeRates() {
        log.info("Exchange rate sync started");

        // Step 1: Discover all active non-USD base currencies
        List<String> currencies = legalEntityRepository
                .findDistinctActiveBaseCurrenciesExcluding("USD");

        if (currencies.isEmpty()) {
            log.info("No non-USD entities found — skipping sync");
            return;
        }

        LocalDate today = LocalDate.now();

        for (String sourceCurrency : currencies) {
            processCurrencyPair(sourceCurrency, "USD", today, 0);
        }

        log.info("Exchange rate sync completed for currencies: {}", currencies);
    }

    // -------------------------------------------------------------------------
    // Retry Logic
    // -------------------------------------------------------------------------

    /**
     * Processes a single currency pair with up to {@link #MAX_RETRIES} attempts.
     * Recursive — each retry increments the attempt count.
     */
    private void processCurrencyPair(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            int attempt
    ) {
        try {
            // Idempotency check: skip if active rate already exists for this date
            if (exchangeRateRepository
                    .findBySourceCurrencyAndTargetCurrencyAndRateDateAndStatus(
                            sourceCurrency, targetCurrency, rateDate, com.af.novadesk.api.common.constants.Status.ACTIVE)
                    .isPresent()) {
                log.debug("Rate already exists for {} → {} on {} — skipping",
                        sourceCurrency, targetCurrency, rateDate);
                return;
            }

            // -----------------------------------------------------------------
            // TODO: Replace with actual external API call when integrated
            // -----------------------------------------------------------------
            // BigDecimal rate = externalApiClient.fetchRate(
            //         sourceCurrency, targetCurrency, rateDate);
            //
            // ExchangeRate saved = exchangeRateRepository.save(ExchangeRate.builder()
            //         .sourceCurrency(sourceCurrency)
            //         .targetCurrency(targetCurrency)
            //         .rateDate(rateDate)
            //         .exchangeRate(rate)
            //         .rateSource(RateSource.API)
            //         .createdBy("system-scheduler")
            //         .build());
            //
            // outboxService.publishSyncCompleted(saved);
            // log.info("Synced {} → {} on {}: rate={}", sourceCurrency, targetCurrency,
            //         rateDate, rate);

            // For now, throw to simulate API unavailability
            throw new UnsupportedOperationException(
                    "External exchange rate API not yet integrated. " +
                    "Implement ExchangeRateApiClient.fetchRate(" +
                    sourceCurrency + ", " + targetCurrency + ", " + rateDate + ")");

        } catch (Exception ex) {
            handleFailure(sourceCurrency, targetCurrency, rateDate, attempt, ex);
        }
    }

    /**
     * Handles a failed attempt. Retries with exponential backoff or moves to
     * the Dead Letter Queue when retries are exhausted.
     */
    private void handleFailure(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            int attempt,
            Exception ex
    ) {
        int nextAttempt = attempt + 1;

        if (nextAttempt < MAX_RETRIES) {
            log.warn("Attempt {}/{} failed for {} → {} on {}. Retrying...",
                    nextAttempt, MAX_RETRIES, sourceCurrency, targetCurrency, rateDate, ex);

            // Exponential backoff: 30s, 2min, 5min
            long backoffSeconds = switch (nextAttempt) {
                case 1 -> 30;
                case 2 -> 120;
                default -> 300;
            };

            try {
                Thread.sleep(backoffSeconds * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Retry sleep interrupted for {} → {} on {}",
                        sourceCurrency, targetCurrency, rateDate);
                return;
            }

            processCurrencyPair(sourceCurrency, targetCurrency, rateDate, nextAttempt);

        } else {
            // All retries exhausted → record to Dead Letter Queue
            log.error("All {} retries failed for {} → {} on {}. Moving to DLQ.",
                    MAX_RETRIES, sourceCurrency, targetCurrency, rateDate, ex);

            outboxService.publishSyncFailed(
                    sourceCurrency, targetCurrency, rateDate,
                    ex.getMessage(), MAX_RETRIES);
        }
    }
}
