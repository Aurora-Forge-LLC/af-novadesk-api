package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.CapitalInjectionApi;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.service.CapitalInjectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapitalInjectionController implements CapitalInjectionApi {

    private final CapitalInjectionService capitalInjectionService;

    public CapitalInjectionController(CapitalInjectionService capitalInjectionService) {
        this.capitalInjectionService = capitalInjectionService;
    }
    @Override
    public ResponseEntity<ApiResponse<CapitalInjectionResponse>> createCapitalInjection(
            @Valid @RequestBody CapitalInjectionRequest request
    ) {
        CapitalInjectionResponse injection = capitalInjectionService.createCapitalInjection(request);
        return ResponseBuilder.created(injection, ApiMessages.CAPITAL_INJECTION_SUCCESS);
    }
}