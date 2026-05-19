package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.service.impl.DefaultExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultExchangeRateService} covering all five resolution paths
 * documented in LLR-FIN-02.3:
 *
 * <ol>
 *   <li>Identity  — source currency == target currency → rate = 1.0</li>
 *   <li>Manual    — caller supplies a validated manual rate</li>
 *   <li>Exact     — exact-date match in the exchange rate table</li>
 *   <li>Lookback  — nearest past rate within the configured look-back window</li>
 *   <li>Missing   — no rate found → {@link MissingExchangeRateException}</li>
 * </ol>
 *
 * Also covers manual-rate validation edge cases (negative, missing justification,
 * missing approver, and the M6 upper-bound ceiling).
 */
@ExtendWith(MockitoExtension.class)
class DefaultExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private ExchangeRateService service;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        FundingProperties props = new FundingProperties("USD", 7, new BigDecimal("10000"));

        service = new DefaultExchangeRateService(exchangeRateRepository, props);
    }

    // =========================================================================
    // Resolution path 1: IDENTITY
    // =========================================================================

    @Nested
    @DisplayName("Path 1 — Identity (same currency)")
    class IdentityResolution {

        @Test
        @DisplayName("Same source and target currency returns rate = 1.0 with IDENTITY source")
        void sameCurrency_returnsIdentityRate() {
            ExchangeRateResolution result = service.resolveRate("USD", "USD", TODAY, null, null, null);

            assertThat(result.rate()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(result.rateSource()).isEqualTo(RateSource.IDENTITY);
            assertThat(result.rateDate()).isEqualTo(TODAY);
        }

        @Test
        @DisplayName("Case-insensitive currency comparison still resolves identity")
        void sameCurrencyCaseInsensitive_returnsIdentityRate() {
            ExchangeRateResolution result = service.resolveRate("usd", "USD", TODAY, null, null, null);

            assertThat(result.rateSource()).isEqualTo(RateSource.IDENTITY);
        }
    }

    // =========================================================================
    // Resolution path 2: MANUAL
    // =========================================================================

    @Nested
    @DisplayName("Path 2 — Manual rate")
    class ManualResolution {

        @Test
        @DisplayName("Valid manual rate is returned with MANUAL source")
        void validManualRate_returnedWithManualSource() {
            BigDecimal manualRate = new BigDecimal("0.012");
            ExchangeRateResolution result = service.resolveRate(
                    "INR", "USD", TODAY, manualRate, "Treasury approved", "John Doe");

            assertThat(result.rate()).isEqualByComparingTo(manualRate);
            assertThat(result.rateSource()).isEqualTo(RateSource.MANUAL);
        }

        @Test
        @DisplayName("Negative manual rate → BadRequestException")
        void negativeManualRate_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolveRate(
                    "INR", "USD", TODAY, new BigDecimal("-0.001"), "justification", "approver"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Missing justification for manual rate → BadRequestException")
        void manualRateWithoutJustification_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolveRate(
                    "INR", "USD", TODAY, new BigDecimal("0.012"), null, "approver"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("justification");
        }

        @Test
        @DisplayName("Missing approver for manual rate → BadRequestException")
        void manualRateWithoutApprover_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolveRate(
                    "INR", "USD", TODAY, new BigDecimal("0.012"), "justification", ""))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("approver");
        }

        @Test
        @DisplayName("M6: Manual rate above ceiling → BadRequestException")
        void manualRateAboveCeiling_throwsBadRequest() {
            // Simulates data-entry typo: 12000 instead of 0.012
            assertThatThrownBy(() -> service.resolveRate(
                    "INR", "USD", TODAY, new BigDecimal("12000"), "justification", "approver"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("ceiling");
        }
    }

    // =========================================================================
    // Resolution path 3: EXACT-DATE lookup
    // =========================================================================

    @Nested
    @DisplayName("Path 3 — Exact-date table lookup")
    class ExactDateResolution {

        @Test
        @DisplayName("Exact-date rate found in table → used with its original source")
        void exactDateRateFound_returnsTableRate() {
            BigDecimal storedRate = new BigDecimal("0.0119");
            ExchangeRate rate = buildExchangeRate("INR", "USD", TODAY, storedRate, RateSource.API);
            when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrencyAndRateDate("INR", "USD", TODAY))
                    .thenReturn(Optional.of(rate));

            ExchangeRateResolution result = service.resolveRate("INR", "USD", TODAY, null, null, null);

            assertThat(result.rate()).isEqualByComparingTo(storedRate);
            assertThat(result.rateSource()).isEqualTo(RateSource.API);
            assertThat(result.rateDate()).isEqualTo(TODAY);
        }
    }

    // =========================================================================
    // Resolution path 4: LOOKBACK
    // =========================================================================

    @Nested
    @DisplayName("Path 4 — Look-back within configured window")
    class LookbackResolution {

        @Test
        @DisplayName("No exact rate but nearest past rate in window found → LOOKBACK source")
        void noExactRate_nearestPastRateUsed() {
            LocalDate staleDate = TODAY.minusDays(3);
            BigDecimal storedRate = new BigDecimal("0.0115");
            ExchangeRate rate = buildExchangeRate("INR", "USD", staleDate, storedRate, RateSource.API);

            when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrencyAndRateDate("INR", "USD", TODAY))
                    .thenReturn(Optional.empty());
            when(exchangeRateRepository.findNearestPastRateWithinWindow(
                    eq("INR"), eq("USD"), eq(TODAY), any(LocalDate.class)))
                    .thenReturn(Optional.of(rate));

            ExchangeRateResolution result = service.resolveRate("INR", "USD", TODAY, null, null, null);

            assertThat(result.rate()).isEqualByComparingTo(storedRate);
            assertThat(result.rateSource()).isEqualTo(RateSource.LOOKBACK);
            assertThat(result.rateDate()).isEqualTo(staleDate);
        }
    }

    // =========================================================================
    // Resolution path 5: MISSING
    // =========================================================================

    @Nested
    @DisplayName("Path 5 — Missing rate → MissingExchangeRateException")
    class MissingResolution {

        @Test
        @DisplayName("No exact rate and no rate in look-back window → MissingExchangeRateException")
        void noRateFound_throwsMissingExchangeRateException() {
            when(exchangeRateRepository.findBySourceCurrencyAndTargetCurrencyAndRateDate("INR", "USD", TODAY))
                    .thenReturn(Optional.empty());
            when(exchangeRateRepository.findNearestPastRateWithinWindow(
                    eq("INR"), eq("USD"), eq(TODAY), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolveRate("INR", "USD", TODAY, null, null, null))
                    .isInstanceOf(MissingExchangeRateException.class)
                    .hasMessageContaining("INR")
                    .hasMessageContaining("USD");
        }

        @Test
        @DisplayName("Blank source currency → BadRequestException")
        void blankSourceCurrency_throwsBadRequest() {
            assertThatThrownBy(() -> service.resolveRate("", "USD", TODAY, null, null, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("blank");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ExchangeRate buildExchangeRate(
            String src, String tgt, LocalDate date, BigDecimal rate, RateSource source) {
        return ExchangeRate.builder()
                .id(UUID.randomUUID())
                .sourceCurrency(src)
                .targetCurrency(tgt)
                .rateDate(date)
                .exchangeRate(rate)
                .rateSource(source)
                .build();
    }
}

