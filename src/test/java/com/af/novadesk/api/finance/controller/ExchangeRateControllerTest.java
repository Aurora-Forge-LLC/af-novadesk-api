package com.af.novadesk.api.finance.controller;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Controller-slice tests for {@link ExchangeRateController}.
 *
 * Covers:
 * - GET /api/v1/finance/exchange-rates (no filters, partial filters, full filters)
 * - GET /api/v1/finance/exchange-rates/{id} → 200 for existing / 404 for missing
 * - FinanceExceptionHandler: NotFoundException → RFC 9457 ProblemDetail (404)
 */
@WebMvcTest(controllers = ExchangeRateController.class)
@WithMockUser
class ExchangeRateControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean  ExchangeRateReadService exchangeRateReadService;
    private static final String BASE_URL = "/api/v1/finance/exchange-rates";
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 18);
    private ExchangeRateSummaryResponse buildDto(UUID id) {
        return new ExchangeRateSummaryResponse(
                id, "INR", "USD", TODAY,
                new BigDecimal("0.012000"), RateSource.API,
                Status.ACTIVE, LocalDateTime.now());
    }
    @Test
    @DisplayName("GET /exchange-rates (no filters)  →  200 with rate list")
    void list_noFilters_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(exchangeRateReadService.list(isNull(), isNull(), isNull()))
                .thenReturn(List.of(buildDto(id)));
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].source_currency").value("INR"))
                .andExpect(jsonPath("$.data[0].target_currency").value("USD"))
                .andExpect(jsonPath("$.data[0].rate_source").value("API"));
    }
    @Test
    @DisplayName("GET /exchange-rates?sourceCurrency=INR  →  filter passed to service")
    void list_withSourceFilter_passedToService() throws Exception {
        when(exchangeRateReadService.list(eq("INR"), isNull(), isNull()))
                .thenReturn(List.of());
        mockMvc.perform(get(BASE_URL).param("sourceCurrency", "INR")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(exchangeRateReadService).list("INR", null, null);
    }
    @Test
    @DisplayName("GET /exchange-rates?sourceCurrency=INR&targetCurrency=USD&rateDate=2026-05-18  →  all filters passed")
    void list_withAllFilters_passedToService() throws Exception {
        when(exchangeRateReadService.list("INR", "USD", TODAY)).thenReturn(List.of());
        mockMvc.perform(get(BASE_URL)
                        .param("sourceCurrency", "INR")
                        .param("targetCurrency", "USD")
                        .param("rateDate", "2026-05-18")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(exchangeRateReadService).list("INR", "USD", TODAY);
    }
    @Test
    @DisplayName("GET /exchange-rates  →  200 with empty list when none match")
    void list_empty_returnsEmptyArray() throws Exception {
        when(exchangeRateReadService.list(any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
    @Test
    @DisplayName("GET /exchange-rates/{id}  →  200 for existing rate")
    void getById_existing_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(exchangeRateReadService.getById(id)).thenReturn(buildDto(id));
        mockMvc.perform(get(BASE_URL + "/" + id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.exchange_rate").value(0.012000));
    }
    @Test
    @DisplayName("GET /exchange-rates/{id}  →  404 ProblemDetail for unknown rate")
    void getById_notFound_returns404ProblemDetail() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(exchangeRateReadService.getById(unknown))
                .thenThrow(new NotFoundException("Exchange rate not found with id: " + unknown));
        mockMvc.perform(get(BASE_URL + "/" + unknown).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString(unknown.toString())));
    }
}
