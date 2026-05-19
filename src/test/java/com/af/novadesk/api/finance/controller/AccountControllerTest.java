package com.af.novadesk.api.finance.controller;
import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.constants.Status;
import com.af.novadesk.api.finance.dto.AccountSummaryResponse;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Controller-slice tests for {@link AccountController}.
 *
 * Uses @WebMvcTest to load only the web layer (controller + exception handler).
 * Covers:
 * - GET /api/v1/finance/accounts          → 200 with account list
 * - GET /api/v1/finance/accounts/{id}     → 200 for known ID / 404 for unknown ID
 * - FinanceExceptionHandler: NotFoundException → RFC 9457 ProblemDetail (404)
 */
@WebMvcTest(controllers = AccountController.class)
@WithMockUser
class AccountControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean  AccountService accountService;
    private static final String BASE_URL = "/api/v1/finance/accounts";
    @Test
    @DisplayName("GET /accounts  →  200 with account list")
    void list_returnsOkWithAccounts() throws Exception {
        UUID id       = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        AccountSummaryResponse dto = new AccountSummaryResponse(
                id, entityId, "FOUNDER_EQUITY", "Founder Equity",
                AccountRole.FOUNDER_EQUITY, AccountType.EQUITY, "USD",
                Status.ACTIVE, LocalDateTime.now());
        when(accountService.list()).thenReturn(List.of(dto));
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].account_role").value("FOUNDER_EQUITY"));
    }
    @Test
    @DisplayName("GET /accounts  →  200 with empty list when no accounts exist")
    void list_noAccounts_returnsEmptyArray() throws Exception {
        when(accountService.list()).thenReturn(List.of());
        mockMvc.perform(get(BASE_URL).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
    @Test
    @DisplayName("GET /accounts/{id}  →  200 for existing account")
    void getById_existing_returnsOk() throws Exception {
        UUID id       = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        AccountSummaryResponse dto = new AccountSummaryResponse(
                id, entityId, "BANK_OPERATING", "Bank Operating",
                AccountRole.BANK_OPERATING, AccountType.ASSET, "INR",
                Status.ACTIVE, LocalDateTime.now());
        when(accountService.getById(id)).thenReturn(dto);
        mockMvc.perform(get(BASE_URL + "/" + id).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }
    @Test
    @DisplayName("GET /accounts/{id}  →  404 ProblemDetail for unknown account")
    void getById_notFound_returns404ProblemDetail() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(accountService.getById(unknown))
                .thenThrow(new NotFoundException("Account not found with id: " + unknown));
        mockMvc.perform(get(BASE_URL + "/" + unknown).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(unknown.toString())));
    }
}
