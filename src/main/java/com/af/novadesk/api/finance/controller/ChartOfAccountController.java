package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.ChartOfAccountApi;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.service.ChartOfAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Chart of Accounts reference endpoints.
 *
 * <p>Base path: {@code /api/v1/finance/chart-of-accounts}</p>
 */
@RestController
@RequiredArgsConstructor
public class ChartOfAccountController implements ChartOfAccountApi {

    private final ChartOfAccountService chartOfAccountService;

    /**
     * GET /api/v1/finance/chart-of-accounts?legalEntityId={id}
     * GET /api/v1/finance/chart-of-accounts?legalEntityId={id}&accountType=EXPENSE
     */
    @Override
    public ResponseEntity<ApiResponse<List<ChartOfAccountDto>>> list(
            UUID legalEntityId,
            AccountType accountType
    ) {
        List<ChartOfAccountDto> data = (accountType != null)
                ? chartOfAccountService.listByEntityAndType(legalEntityId, accountType)
                : chartOfAccountService.listByEntity(legalEntityId);

        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
