package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.dto.TaxConfigurationDto;
import com.af.novadesk.api.payroll.dto.TaxSlabDto;
import com.af.novadesk.api.payroll.entity.TaxConfiguration;
import com.af.novadesk.api.payroll.entity.TaxSlab;
import com.af.novadesk.api.payroll.exception.PayrollProcessingException;
import com.af.novadesk.api.payroll.exception.TaxConfigurationNotFoundException;
import com.af.novadesk.api.payroll.repository.TaxConfigurationRepository;
import com.af.novadesk.api.payroll.service.TaxConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaxConfigurationServiceImpl implements TaxConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(TaxConfigurationServiceImpl.class);

    private final TaxConfigurationRepository repository;
    private final LegalEntityRepository legalEntityRepository;

    public TaxConfigurationServiceImpl(TaxConfigurationRepository repository,
                                       LegalEntityRepository legalEntityRepository) {
        this.repository = repository;
        this.legalEntityRepository = legalEntityRepository;
    }

    /**
     * Maps a {@link CountryCode} to the corresponding {@link Jurisdiction}.
     */
    private static Jurisdiction toJurisdiction(CountryCode countryCode) {
        return switch (countryCode) {
            case NP -> Jurisdiction.NEPAL;
            case IN -> Jurisdiction.INDIA;
            case US -> Jurisdiction.USA;
        };
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
        // Resolve jurisdiction from the legal entity's country
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new PayrollProcessingException(
                        "Legal entity not found: " + request.getLegalEntityId()));
        Jurisdiction jurisdiction = toJurisdiction(legalEntity.getCountry());

        // DIAGNOSTIC: Check if a config already exists for this entity+jurisdiction
        var existing = repository.findByLegalEntityIdAndJurisdiction(request.getLegalEntityId(), jurisdiction);
        if (existing.isPresent()) {
            TaxConfiguration existConfig = existing.get();
            log.warn("DIAGNOSTIC: Attempted to create duplicate tax config for entity={}, jurisdiction={}. "
                            + "Existing config id={}, status={}, isActive={}, effectiveFrom={}, effectiveTo={}",
                    request.getLegalEntityId(), jurisdiction,
                    existConfig.getId(), existConfig.getStatus(), existConfig.getIsActive(),
                    existConfig.getEffectiveFrom(), existConfig.getEffectiveTo());
        } else {
            log.info("DIAGNOSTIC: No existing tax config found for entity={}, jurisdiction={}. Proceeding with create.",
                    request.getLegalEntityId(), jurisdiction);
        }

        TaxConfiguration config = new TaxConfiguration();
        config.setLegalEntity(legalEntity);
        config.setTaxName(request.getTaxName());
        config.setJurisdiction(jurisdiction);
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
                        .ratePercent(slabDto.getRatePercent())
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
    public void deleteTaxConfiguration(UUID configId) {
        TaxConfiguration config = repository.findById(configId)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(
                        "Tax configuration not found: " + configId));
        config.setStatus(com.af.novadesk.api.common.constants.Status.DELETED);
        config.setIsActive(false);
        repository.save(config);
    }

    @Override
    public void hardDeleteTaxConfiguration(UUID configId) {
        if (!repository.existsById(configId)) {
            throw new TaxConfigurationNotFoundException(
                    "Tax configuration not found: " + configId);
        }
        repository.deleteById(configId);
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
                .taxName(entity.getTaxName())
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
                .taxSlabs(entity.getTaxSlabs() != null
                        ? entity.getTaxSlabs().stream().map(s ->
                                TaxSlabDto.builder()
                                        .id(s.getId())
                                        .slabOrder(s.getSlabOrder())
                                        .incomeFrom(s.getIncomeFrom())
                                        .incomeTo(s.getIncomeTo())
                                        .ratePercent(s.getRatePercent())
                                        .isAnnual(s.getIsAnnual())
                                        .description(s.getDescription())
                                        .createdAt(s.getCreatedAt())
                                        .updatedAt(s.getUpdatedAt())
                                        .build())
                                .collect(Collectors.toList())
                        : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
