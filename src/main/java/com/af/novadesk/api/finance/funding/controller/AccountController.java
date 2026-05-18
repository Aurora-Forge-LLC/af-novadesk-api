package com.af.novadesk.api.finance.funding.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.funding.api.AccountApi;
import com.af.novadesk.api.finance.funding.dto.AccountSummaryResponse;
import com.af.novadesk.api.finance.funding.service.AccountService;
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

    @Override
    public ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> list() {
        List<AccountSummaryResponse> data = accountService.list();
        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<AccountSummaryResponse>> getById(UUID id) {
        return ResponseBuilder.ok(accountService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }
}

