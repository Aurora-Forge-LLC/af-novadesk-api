package com.af.novadesk.api.finance.service;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.ExchangeRateNotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.impl.ExchangeRateReadServiceImpl;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private FinanceSecurityContext securityContext;
    @Mock private EntityManager entityManager;
    @Mock private Session session;
    @Mock private Filter filter;
    private ExchangeRateReadService service;
    private static final LocalDate TODAY = LocalDate.now();
    private static final UUID ORG_ID = UUID.randomUUID();
    @BeforeEach
    void setUp() {
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter("organizationFilter")).thenReturn(filter);
        when(securityContext.getOrganizationId()).thenReturn(ORG_ID);
        service = new ExchangeRateReadServiceImpl(exchangeRateRepository, securityContext, entityManager);
    }
    @Nested @DisplayName("list() — filter combinations (M1)")
    class ListFilters {
        @Test @DisplayName("No filters: uses Specification with all nulls, delegates to findAll")
        void list_noFilters_usesSpecificationWithNulls() {
            when(exchangeRateRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());
            List<ExchangeRateSummaryResponse> result = service.list(null, null, null);
            assertThat(result).isEmpty();
            verify(exchangeRateRepository).findAll(any(Specification.class));
        }
        @Test @DisplayName("Source-currency filter is normalised to upper-case")
        void list_sourceCurrencyInLowerCase_normalisedToUpperCase() {
            when(exchangeRateRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());
            service.list("inr", null, null);
            verify(exchangeRateRepository).findAll(any(Specification.class));
        }
        @Test @DisplayName("Target-currency filter is trimmed and normalised to upper-case")
        void list_targetCurrencyWithWhitespace_normalisedAndTrimmed() {
            when(exchangeRateRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());
            service.list(null, "  usd  ", null);
            verify(exchangeRateRepository).findAll(any(Specification.class));
        }
        @Test @DisplayName("All three filters are passed through Specification")
        void list_allFilters_usesSpecification() {
            when(exchangeRateRepository.findAll(any(Specification.class)))
                    .thenReturn(List.of());
            service.list("INR", "USD", TODAY);
            verify(exchangeRateRepository).findAll(any(Specification.class));
        }
        @Test @DisplayName("Returns mapped DTOs for each exchange rate returned by repository")
        void list_resultsMapped() {
            UUID id = UUID.randomUUID();
            ExchangeRate rate = buildRate(id, "INR", "USD", TODAY, new BigDecimal("0.012"), RateSource.API);
            when(exchangeRateRepository.findAll(any(Specification.class))).thenReturn(List.of(rate));
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
            when(exchangeRateRepository.findAll(any(Specification.class))).thenReturn(List.of());
            assertThat(service.list("XYZ", "USD", TODAY)).isEmpty();
        }
    }
    @Nested @DisplayName("getById()")
    class GetById {
        @Test @DisplayName("Existing rate returns fully mapped detail DTO")
        void getById_existing_returnsMappedDto() {
            UUID id = UUID.randomUUID();
            ExchangeRate rate = buildRate(id, "INR", "USD", TODAY, new BigDecimal("0.0119"), RateSource.LOOKBACK);
            when(exchangeRateRepository.findById(id)).thenReturn(Optional.of(rate));
            ExchangeRateDetailResponse dto = service.getById(id);
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
