package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.api.CapitalInjectionApi;
import com.af.novadesk.api.finance.dto.CapitalInjectionDetailDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionPageDto;
import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.dto.CapitalInjectionStatusRequest;
import com.af.novadesk.api.finance.dto.InterEntityTransferDto;
import com.af.novadesk.api.finance.service.CapitalInjectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CapitalInjectionController implements CapitalInjectionApi {

    private final CapitalInjectionService capitalInjectionService;

    public CapitalInjectionController(CapitalInjectionService capitalInjectionService) {
        this.capitalInjectionService = capitalInjectionService;
    }

    @Override
    public ResponseEntity<ApiResponse<CapitalInjectionResponse>> createCapitalInjection(
            @Valid CapitalInjectionRequest request
    ) {
        CapitalInjectionResponse injection = capitalInjectionService.createCapitalInjection(request);
        return ResponseBuilder.created(injection, ApiMessages.CAPITAL_INJECTION_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<CapitalInjectionPageDto>> listCapitalInjections(
            String entityCode, int page, int size
    ) {
        CapitalInjectionPageDto result = capitalInjectionService.listCapitalInjections(entityCode, page, size);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<CapitalInjectionDetailDto>> getCapitalInjection(UUID id) {
        CapitalInjectionDetailDto detail = capitalInjectionService.getCapitalInjectionDetail(id);
        return ResponseBuilder.ok(detail, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> updateCapitalInjectionStatus(
            UUID id, @Valid CapitalInjectionStatusRequest request
    ) {
        capitalInjectionService.updateCapitalInjectionStatus(id, request);
        return ResponseBuilder.ok(null, "Capital injection status updated");
    }

    @Override
    public ResponseEntity<ApiResponse<InterEntityTransferDto>> getInterEntityTransfer(UUID transferId) {
        InterEntityTransferDto transfer = capitalInjectionService.getInterEntityTransfer(transferId);
        return ResponseBuilder.ok(transfer, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
