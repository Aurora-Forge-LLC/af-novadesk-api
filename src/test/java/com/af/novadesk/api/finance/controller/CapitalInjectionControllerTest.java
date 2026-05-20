package com.af.novadesk.api.finance.controller;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.service.CapitalInjectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Controller-slice tests for {@link CapitalInjectionController}.
 *
 * Covers:
 * - POST /api/v1/finance/funding/capital-injections
 *   ✓  201 Created on happy path
 *   ✓  400 for Bean Validation failures (missing/invalid fields)
 *   ✓  400 from BadRequestException thrown by service
 *   ✓  404 from NotFoundException thrown by service
 *   ✓  422 from MissingExchangeRateException thrown by service
 * - FinanceExceptionHandler: all exception types produce RFC 9457 ProblemDetail
 */
@WebMvcTest(controllers = CapitalInjectionController.class)
@WithMockUser
class CapitalInjectionControllerTest {
    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @MockBean  CapitalInjectionService capitalInjectionService;
    private static final String URL     = "/api/v1/finance/funding/capital-injections";
    private static final UUID   SRC_ID  = UUID.randomUUID();
    private static final UUID   DST_ID  = UUID.randomUUID();
    // =========================================================================
    // Happy path — 201 Created
    // =========================================================================
    @Nested @DisplayName("201 Created — happy path")
    class HappyPath {
        @Test
        @DisplayName("Valid FOUNDER_EQUITY request → 201 with response body")
        void create_validRequest_returns201() throws Exception {
            UUID injectionId = UUID.randomUUID();
            UUID journalId   = UUID.randomUUID();
            CapitalInjectionResponse resp = CapitalInjectionResponse.builder()
                    .capitalInjectionId(injectionId)
                    .journalId(journalId)
                    .targetEntityCode("INDIA")
                    .amountLocal(new BigDecimal("100000.0000"))
                    .currencyLocal("INR")
                    .amountUsd(new BigDecimal("1200.0000"))
                    .exchangeRateUsed(new BigDecimal("0.012000"))
                    .rateDateUsed(LocalDate.of(2026, 5, 18))
                    .rateSource(RateSource.API)
                    .message("Capital injection created and posted to ledger")
                    .build();
            when(capitalInjectionService.createCapitalInjection(any())).thenReturn(resp);
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.capital_injection_id").value(injectionId.toString()))
                    .andExpect(jsonPath("$.data.target_entity_code").value("INDIA"))
                    .andExpect(jsonPath("$.data.amount_local").value(100000.0000))
                    .andExpect(jsonPath("$.data.currency_local").value("INR"))
                    .andExpect(jsonPath("$.data.rate_source").value("API"));
        }
    }
    // =========================================================================
    // 400 — Bean Validation failures
    // =========================================================================
    @Nested @DisplayName("400 Bad Request — Bean Validation")
    class BeanValidation {
        @Test
        @DisplayName("Missing targetEntityCode → 400 ProblemDetail (validation)")
        void create_missingTargetEntityCode_returns400() throws Exception {
            String body = """
                    {
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 1000.00,
                      "funding_date": "2026-05-18",
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        @Test
        @DisplayName("Missing fundingSource → 400 ProblemDetail (validation)")
        void create_missingFundingSource_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "amount": 1000.00,
                      "funding_date": "2026-05-18",
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        @Test
        @DisplayName("Amount below minimum (0.00) → 400 ProblemDetail (validation)")
        void create_amountBelowMinimum_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 0.00,
                      "funding_date": "2026-05-18",
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Amount above DECIMAL(19,4) ceiling → 400 ProblemDetail (validation)")
        void create_amountAboveMaximum_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 1000000000000000.0000,
                      "funding_date": "2026-05-18",
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Amount with more than 4 decimal places → 400 ProblemDetail (validation)")
        void create_amountWithTooManyDecimalPlaces_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 1000.12345,
                      "funding_date": "2026-05-18",
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Missing fundingDate → 400 ProblemDetail (validation)")
        void create_missingFundingDate_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 1000.00,
                      "source_account_id": "%s"
                    }""".formatted(SRC_ID);
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }


        @Test
        @DisplayName("Missing sourceAccountId → 400 ProblemDetail (validation)")
        void create_missingSourceAccountId_returns400() throws Exception {
            String body = """
                    {
                      "target_entity_code": "INDIA",
                      "funding_source": "FOUNDER_EQUITY",
                      "amount": 1000.00,
                      "funding_date": "2026-05-18"
                    }""";
            mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }
    // =========================================================================
    // 400 — Service-layer BadRequestException
    // =========================================================================
    @Nested @DisplayName("400 Bad Request — service BadRequestException")
    class ServiceBadRequest {
        @Test
        @DisplayName("Future funding date → service throws BadRequestException → 400 ProblemDetail")
        void create_futureDateRejectedByService_returns400() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new BadRequestException("Funding date cannot be in the future"));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("future")));
        }
        @Test
        @DisplayName("Entity not approved → service throws BadRequestException → 400 ProblemDetail")
        void create_entityNotApproved_returns400() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new BadRequestException("Entity INDIA is not yet approved"));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("not yet approved")));
        }
        @Test
        @DisplayName("Same source/destination → service throws BadRequestException → 400 ProblemDetail")
        void create_sameAccounts_returns400() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new BadRequestException("Source and destination accounts must be different"));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("must be different")));
        }
    }
    // =========================================================================
    // 404 — Service-layer NotFoundException
    // =========================================================================
    @Nested @DisplayName("404 Not Found — service NotFoundException")
    class ServiceNotFound {
        @Test
        @DisplayName("Unknown entity → service throws NotFoundException → 404 ProblemDetail")
        void create_entityNotFound_returns404() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new NotFoundException("Legal entity not found: INDIA"));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("INDIA")));
        }
        @Test
        @DisplayName("Unknown account → service throws NotFoundException → 404 ProblemDetail")
        void create_accountNotFound_returns404() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new NotFoundException("Account not found: " + SRC_ID));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
    // =========================================================================
    // 422 — MissingExchangeRateException
    // =========================================================================
    @Nested @DisplayName("422 Unprocessable Entity — MissingExchangeRateException")
    class MissingExchangeRate {
        @Test
        @DisplayName("No rate for INR→USD → 422 ProblemDetail with MISSING_EXCHANGE_RATE type")
        void create_missingRate_returns422() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new MissingExchangeRateException("No exchange rate found for INR→USD"));
            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.title").value("Missing Exchange Rate"))
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("INR")));
        }
    }

    // =========================================================================
    // 500 — Internal state errors (sanitised)
    // =========================================================================
    @Nested @DisplayName("500 Internal Server Error — IllegalStateException")
    class InternalStateError {
        @Test
        @DisplayName("Service IllegalStateException -> 500 ProblemDetail with sanitised message")
        void create_illegalState_returns500SanitisedProblemDetail() throws Exception {
            when(capitalInjectionService.createCapitalInjection(any()))
                    .thenThrow(new IllegalStateException("No authenticated user in security context"));

            mockMvc.perform(post(URL)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validFounderEquityRequest()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.type").value("urn:af:novadesk:error:internal"))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    .andExpect(jsonPath("$.detail").value(
                            "An unexpected internal error occurred. Please contact support."));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private String validFounderEquityRequest() {
        return """
                {
                  "target_entity_code": "INDIA",
                  "funding_source": "FOUNDER_EQUITY",
                  "amount": 100000.00,
                  "funding_date": "2026-05-18",
                  "source_account_id": "%s",
                  "destination_account_id": "%s"
                }""".formatted(SRC_ID, DST_ID);
    }
}
