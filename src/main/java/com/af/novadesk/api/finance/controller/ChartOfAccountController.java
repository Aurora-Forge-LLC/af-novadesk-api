package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.ChartOfAccountApi;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.service.ChartOfAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ChartOfAccountController implements ChartOfAccountApi {

    private final ChartOfAccountService chartOfAccountService;

    @Override
    public ResponseEntity<ApiResponse<PageResponse<ChartOfAccountDto>>> list(
            UUID legalEntityId, String q,
            AccountType accountType, Status status, Boolean postable,
            UUID parentAccountId, Boolean systemGenerated,
            int page, int size, String sortBy, String sortDir) {
        return ResponseBuilder.ok(
                chartOfAccountService.listFiltered(
                        legalEntityId, q, accountType, status, postable,
                        parentAccountId, systemGenerated,
                        page, size, sortBy, sortDir),
                ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
