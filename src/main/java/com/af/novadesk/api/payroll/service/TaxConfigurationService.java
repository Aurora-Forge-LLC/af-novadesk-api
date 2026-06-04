package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.dto.TaxConfigurationDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for Tax Configuration management (LLR-PAY-04.6).
 */
public interface TaxConfigurationService {

    TaxConfigurationDto getTaxConfiguration(UUID legalEntityId, Jurisdiction jurisdiction);

    TaxConfigurationDto createTaxConfiguration(TaxConfigurationDto request);

    TaxConfigurationDto updateTaxConfiguration(UUID configId, TaxConfigurationDto request);

    List<TaxConfigurationDto> listAllTaxConfigurations();

    List<TaxConfigurationDto> listTaxConfigurationsByEntity(UUID legalEntityId);
}
