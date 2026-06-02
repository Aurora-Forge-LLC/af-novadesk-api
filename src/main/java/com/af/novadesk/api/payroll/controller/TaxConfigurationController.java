package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.TaxConfigurationApi;
import com.af.novadesk.api.payroll.dto.TaxConfigurationDto;
import com.af.novadesk.api.payroll.service.TaxConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TaxConfigurationController implements TaxConfigurationApi {

    private final TaxConfigurationService taxConfigurationService;

    public TaxConfigurationController(TaxConfigurationService taxConfigurationService) {
        this.taxConfigurationService = taxConfigurationService;
    }

    @Override
    public ResponseEntity<ApiResponse<List<TaxConfigurationDto>>> listByEntity(UUID legalEntityId) {
        List<TaxConfigurationDto> result = taxConfigurationService.listTaxConfigurationsByEntity(legalEntityId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<TaxConfigurationDto>> getConfig(UUID id) {
        TaxConfigurationDto result = null; // fetch by ID
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<TaxConfigurationDto>> createConfig(@Valid TaxConfigurationDto request) {
        TaxConfigurationDto result = taxConfigurationService.createTaxConfiguration(request);
        return ResponseBuilder.created(result, ApiMessages.RECORD_CREATED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<TaxConfigurationDto>> updateConfig(UUID id, @Valid TaxConfigurationDto request) {
        TaxConfigurationDto result = taxConfigurationService.updateTaxConfiguration(id, request);
        return ResponseBuilder.ok(result, ApiMessages.RECORD_UPDATED_SUCCESS);
    }
}
