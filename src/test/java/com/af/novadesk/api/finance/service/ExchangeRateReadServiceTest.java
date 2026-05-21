package com.af.novadesk.api.finance.service;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.ExchangeRateNotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.service.impl.ExchangeRateReadServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
/**
 * Unit tests for {@link ExchangeRateReadServiceImpl}.
 *
 * Covers:
 * - list() with all combinations of optional filters (M1: no silent full-table scan)
 * - Currency code normalisation to upper-case before repository call
 * - getById(): successful retrieval and NotFoundException for unknown ID
 * - DTO field mapping (all fields populated from entity)
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateReadServiceTest {
    @Mock private ExchangeRateRepository exchangeRateRepository;
    private ExchangeRateReadService service;
    private static final LocalDate TODAY = LocalDate.now();
    @BeforeEach
    void setUp() { service = new ExchangeRateReadServiceImpl(exchangeRateRepository); }
    @Nested @DisplayName("list() — filter combinations (M1)")
    class ListFilters {
        @Test @DisplayName("No filters: null parameters passed to repository (no silent full-scan)")
        void list_noFilters_passesNullsToRepository() {
            when(exchangeRateRepository.findByFilters(isNull(), isNull(), isNull()))
                    .thenReturn(List.of());
            service.list(null, null, null);
            verify(exchangeRateRepository).findByFilters(null, null, null);
        }
        @Test @DisplayName("Source-currency filter is normalised to upper-case and passed through")
        void list_sourceCurrencyInLowerCase_normalisedToUpperCase() {
            when(exchangeRateRepository.findByFilters(eq("INR"), isNull(), isNull()))
                    .thenReturn(List.of());
            service.list("inr", null, null);
            verify(exchangeRateRepository).findByFilters("INR", null, null);
        }
        @Test @DisplayName("Target-currency filter is trimmed and normalised to upper-case")
        void list_targetCurrencyWithWhitespace_normalisedAndTrimmed() {
            when(exchangeRateRepository.findByFilters(isNull(), eq("USD"), isNull()))
                    .thenReturn(List.of());
            service.list(null, "  usd  ", null);
            verify(exchangeRateRepository).findByFilters(null, "USD", null);
        }
        @Test @DisplayName("All three filters are passed through simultaneously")
        void list_allFilters_passedToRepository() {
            when(exchangeRateRepository.findByFilters("INR", "USD", TODAY))
                    .thenReturn(List.of());
            service.list("INR", "USD", TODAY);
            verify(exchangeRateRepository).findByFilters("INR", "USD", TODAY);
        }
        @Test @DisplayName("Returns mapped DTOs for each exchange rate returned by repository")
        void list_resultsMapped() {
            UUID id = UUID.randomUUID();
            ExchangeRate rate = buildRate(id, "INR", "USD", TODAY, new BigDecimal("0.012"), RateSource.API);
            when(exchangeRateRepository.findByFilters(any(), any(), any())).thenReturn(List.of(rate));
            List<ExchangeRateSummaryResponse> results = service.list(null, null, null);
            assertThat(results).hasSize(1);
            ExchangeRateSummaryResponse dto = results.get(0);
            assertThat(dto.id()).isEqualTo(id);
            assertThat(dto.sourceCurrency()).isEqualTo("INR");
            assertThat(dto.targetCurrency()).isEqualTo("USD");
            assertThat(dto.rateDate()).isEqualTo(TODAY);
            assertThat(dto.exchangeRate()).isEqualByComparingTo("0.012");
            assertThat(dto.rateSource()).isEqualTo(RateSource.API);
        }
        @Test @DisplayName("Empty list returned when repository finds no matching rates")
        void list_noMatches_returnsEmpty() {
            when(exchangeRateRepository.findByFilters(any(), any(), any())).thenReturn(List.of());
            assertThat(service.list("XYZ", "USD", TODAY)).isEmpty();
        }
    }
    @Nested @DisplayName("getById()")
    class GetById {
        @Test @DisplayName("Existing rate returns fully mapped summary DTO")
        void getById_existing_returnsMappedDto() {
            UUID id = UUID.randomUUID();
            ExchangeRate rate = buildRate(id, "INR", "USD", TODAY, new BigDecimal("0.0119"), RateSource.LOOKBACK);
            when(exchangeRateRepository.findById(id)).thenReturn(Optional.of(rate));
            ExchangeRateSummaryResponse dto = service.getById(id);
            assertThat(dto.id()).isEqualTo(id);
            assertThat(dto.sourceCurrency()).isEqualTo("INR");
            assertThat(dto.targetCurrency()).isEqualTo("USD");
            assertThat(dto.exchangeRate()).isEqualByComparingTo("0.0119");
            assertThat(dto.rateSource()).isEqualTo(RateSource.LOOKBACK);
        }
        @Test @DisplayName("Non-existent ID throws NotFoundException with ID in message")
        void getById_notFound_throwsNotFoundException() {
            UUID missing = UUID.randomUUID();
            when(exchangeRateRepository.findById(missing)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(missing))
                    .isInstanceOf(ExchangeRateNotFoundException.class)
                    .hasMessageContaining(missing.toString());
        }
    }
    // =========================================================================
    // Helpers
    // =========================================================================
    private ExchangeRate buildRate(UUID id, String src, String tgt, LocalDate date,
                                   BigDecimal rate, RateSource source) {
        return ExchangeRate.builder()
                .id(id)
                .sourceCurrency(src)
                .targetCurrency(tgt)
                .rateDate(date)
                .exchangeRate(rate)
                .rateSource(source)
                .build();
    }
}
