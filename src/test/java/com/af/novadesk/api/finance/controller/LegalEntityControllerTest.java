package com.af.novadesk.api.finance.controller;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryResponse;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.service.LegalEntityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Controller-slice tests for {@link LegalEntityController}.
 *
 * Covers:
 * - GET /api/v1/finance/legal-entities        → 200 with entity list
 * - GET /api/v1/finance/legal-entities/{id}   → 200 for known ID / 404 for unknown
 * - FinanceExceptionHandler: NotFoundException → RFC 9457 ProblemDetail (404)
 */
@WebMvcTest(controllers = LegalEntityController.class)
@WithMockUser
class LegalEntityControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean  LegalEntityService legalEntityService;
    private static final String BASE_URL = "/api/v1/finance/legal-entities";
    private LegalEntitySummaryResponse buildDto(UUID id, String code, CountryCode country, String currency) {
        return new LegalEntitySummaryResponse(
                id, code + " Entity", code, country, currency,
                ApprovalStatus.APPROVED, Status.ACTIVE,
                LocalDate.of(2020, 1, 1), LocalDateTime.now());
    }
    @Test
    @DisplayName("GET /legal-entities  →  200 with entity list")
    void list_returnsOkWithEntities() throws Exception {
        UUID id = UUID.randomUUID();
        LegalEntitySummaryResponse dto = buildDto(id, "INDIA", CountryCode.IN, "INR");
        when(legalEntityService.list()).thenReturn(List.of(dto));
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].entity_code").value("INDIA"))
                .andExpect(jsonPath("$.data[0].base_currency").value("INR"))
                .andExpect(jsonPath("$.data[0].approval_status").value("APPROVED"));
    }
    @Test
    @DisplayName("GET /legal-entities  →  200 with empty list when none exist")
    void list_empty_returnsEmptyArray() throws Exception {
        when(legalEntityService.list()).thenReturn(List.of());
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
    @Test
    @DisplayName("GET /legal-entities/{id}  →  200 for existing entity")
    void getById_existing_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        LegalEntitySummaryResponse dto = buildDto(id, "US", CountryCode.US, "USD");
        when(legalEntityService.getById(id)).thenReturn(dto);
        mockMvc.perform(get(BASE_URL + "/" + id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.entity_code").value("US"));
    }
    @Test
    @DisplayName("GET /legal-entities/{id}  →  404 ProblemDetail for unknown entity")
    void getById_notFound_returns404ProblemDetail() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(legalEntityService.getById(unknown))
                .thenThrow(new NotFoundException("Legal entity not found with id: " + unknown));
        mockMvc.perform(get(BASE_URL + "/" + unknown).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(unknown.toString())));
    }
}
