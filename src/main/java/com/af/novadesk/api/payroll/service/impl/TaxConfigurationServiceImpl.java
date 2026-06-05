package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.dto.TaxConfigurationDto;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;
import com.af.novadesk.api.payroll.entity.TaxSlab;
import com.af.novadesk.api.payroll.exception.TaxConfigurationNotFoundException;
import com.af.novadesk.api.payroll.repository.TaxConfigurationRepository;
import com.af.novadesk.api.payroll.service.TaxConfigurationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaxConfigurationServiceImpl implements TaxConfigurationService {

    private final TaxConfigurationRepository repository;

    public TaxConfigurationServiceImpl(TaxConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public TaxConfigurationDto getTaxConfiguration(UUID legalEntityId, Jurisdiction jurisdiction) {
        TaxConfiguration config = repository.findByLegalEntityIdAndJurisdiction(legalEntityId, jurisdiction)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(legalEntityId, jurisdiction));
        return toDto(config);
    }

    @Override
    public TaxConfigurationDto createTaxConfiguration(TaxConfigurationDto request) {
        TaxConfiguration config = new TaxConfiguration();
        config.setJurisdiction(request.getJurisdiction());
        config.setSsfEmployeeRate(request.getSsfEmployeeRate());
        config.setSsfEmployerRate(request.getSsfEmployerRate());
        config.setSsfMaxCapAmount(request.getSsfMaxCapAmount());
        config.setPfEmployeeRate(request.getPfEmployeeRate());
        config.setPfEmployerRate(request.getPfEmployerRate());
        config.setPfMaxCapAmount(request.getPfMaxCapAmount());
        config.setProfessionalTaxAmount(request.getProfessionalTaxAmount());
        config.setProfessionalTaxState(request.getProfessionalTaxState());
        config.setEffectiveFrom(request.getEffectiveFrom());
        config.setEffectiveTo(request.getEffectiveTo());
        config.setIsActive(true);
        config.setStatus(Status.ACTIVE);

        if (request.getTaxSlabs() != null) {
            for (int i = 0; i < request.getTaxSlabs().size(); i++) {
                var slabDto = request.getTaxSlabs().get(i);
                TaxSlab slab = TaxSlab.builder()
                        .taxConfiguration(config)
                        .slabOrder(slabDto.getSlabOrder() != null ? slabDto.getSlabOrder() : i + 1)
                        .incomeFrom(slabDto.getIncomeFrom())
                        .incomeTo(slabDto.getIncomeTo())
                        .taxRate(slabDto.getTaxRate())
                        .isAnnual(slabDto.getIsAnnual() != null ? slabDto.getIsAnnual() : true)
                        .description(slabDto.getDescription())
                        .status(Status.ACTIVE)
                        .build();
                config.getTaxSlabs().add(slab);
            }
        }

        config = repository.save(config);
        return toDto(config);
    }

    @Override
    public TaxConfigurationDto updateTaxConfiguration(UUID configId, TaxConfigurationDto request) {
        TaxConfiguration config = repository.findById(configId)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(
                        "Tax configuration not found: " + configId));
        if (request.getSsfEmployeeRate() != null) config.setSsfEmployeeRate(request.getSsfEmployeeRate());
        if (request.getSsfEmployerRate() != null) config.setSsfEmployerRate(request.getSsfEmployerRate());
        if (request.getSsfMaxCapAmount() != null) config.setSsfMaxCapAmount(request.getSsfMaxCapAmount());
        if (request.getPfEmployeeRate() != null) config.setPfEmployeeRate(request.getPfEmployeeRate());
        if (request.getPfEmployerRate() != null) config.setPfEmployerRate(request.getPfEmployerRate());
        if (request.getPfMaxCapAmount() != null) config.setPfMaxCapAmount(request.getPfMaxCapAmount());
        if (request.getProfessionalTaxAmount() != null) config.setProfessionalTaxAmount(request.getProfessionalTaxAmount());
        if (request.getIsActive() != null) config.setIsActive(request.getIsActive());
        config = repository.save(config);
        return toDto(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationDto> listAllTaxConfigurations() {
        return repository.findAll().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationDto> listTaxConfigurationsByEntity(UUID legalEntityId) {
        return repository.findByLegalEntityId(legalEntityId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    private TaxConfigurationDto toDto(TaxConfiguration entity) {
        return TaxConfigurationDto.builder()
                .id(entity.getId())
                .jurisdiction(entity.getJurisdiction())
                .ssfEmployeeRate(entity.getSsfEmployeeRate())
                .ssfEmployerRate(entity.getSsfEmployerRate())
                .ssfMaxCapAmount(entity.getSsfMaxCapAmount())
                .pfEmployeeRate(entity.getPfEmployeeRate())
                .pfEmployerRate(entity.getPfEmployerRate())
                .pfMaxCapAmount(entity.getPfMaxCapAmount())
                .professionalTaxAmount(entity.getProfessionalTaxAmount())
                .professionalTaxState(entity.getProfessionalTaxState())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .isActive(entity.getIsActive())
                .taxSlabs(entity.getTaxSlabs() != null ? entity.getTaxSlabs().stream().map(s ->
                        com.af.novadesk.api.payroll.dto.TaxSlabDto.builder()
                                .id(s.getId())
                                .slabOrder(s.getSlabOrder())
                                .incomeFrom(s.getIncomeFrom())
                                .incomeTo(s.getIncomeTo())
                                .taxRate(s.getTaxRate())
                                .isAnnual(s.getIsAnnual())
                                .description(s.getDescription())
                                .createdAt(s.getCreatedAt())
                                .updatedAt(s.getUpdatedAt())
                                .build()).collect(Collectors.toList()) : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
