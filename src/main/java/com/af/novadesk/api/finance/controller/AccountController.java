package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.AccountApi;
import com.af.novadesk.api.finance.dto.AccountSummaryResponse;
import com.af.novadesk.api.finance.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AccountController implements AccountApi {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    public ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> list(UUID legalEntityId) {
        List<AccountSummaryResponse> data = (legalEntityId != null)
                ? accountService.listByEntityId(legalEntityId)
                : accountService.list();
        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getById(UUID id) {
        return ResponseBuilder.ok(accountService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }
}

