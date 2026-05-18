package com.af.novadesk.api.finance.funding.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.funding.api.LegalEntityApi;
import com.af.novadesk.api.finance.funding.dto.LegalEntitySummaryResponse;
import com.af.novadesk.api.finance.funding.service.LegalEntityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LegalEntityController implements LegalEntityApi {

    private final LegalEntityService legalEntityService;

    public LegalEntityController(LegalEntityService legalEntityService) {
        this.legalEntityService = legalEntityService;
    }

    @Override
    public ResponseEntity<ApiResponse<List<LegalEntitySummaryResponse>>> list() {
        List<LegalEntitySummaryResponse> data = legalEntityService.list();
        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<LegalEntitySummaryResponse>> getById(UUID id) {
        return ResponseBuilder.ok(legalEntityService.getById(id), ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }
}

