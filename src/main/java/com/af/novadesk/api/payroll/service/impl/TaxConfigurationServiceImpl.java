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
import com.af.novadesk.api.payroll.exception.InvalidTaxConfigurationException;
import com.af.novadesk.api.payroll.exception.PayrollProcessingException;
import com.af.novadesk.api.payroll.exception.TaxConfigurationNotFoundException;
import com.af.novadesk.api.payroll.repository.TaxConfigurationRepository;
import com.af.novadesk.api.payroll.repository.TaxSlabRepository;
import com.af.novadesk.api.payroll.service.TaxConfigurationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaxConfigurationServiceImpl implements TaxConfigurationService {

    private final TaxConfigurationRepository repository;
    private final TaxSlabRepository taxSlabRepository;
    private final LegalEntityRepository legalEntityRepository;

    public TaxConfigurationServiceImpl(TaxConfigurationRepository repository,
                                       TaxSlabRepository taxSlabRepository,
                                       LegalEntityRepository legalEntityRepository) {
        this.repository = repository;
        this.taxSlabRepository = taxSlabRepository;
        this.legalEntityRepository = legalEntityRepository;
    }

    /**
     * Maps a {@link CountryCode} to the corresponding {@link Jurisdiction}.
     */
    static Jurisdiction toJurisdiction(CountryCode countryCode) {
        return switch (countryCode) {
            case NP -> Jurisdiction.NEPAL;
            case IN -> Jurisdiction.INDIA;
            case US -> Jurisdiction.USA;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationDto> getTaxConfigurations(UUID legalEntityId, Jurisdiction jurisdiction) {
        List<TaxConfiguration> configs = repository.findByLegalEntityIdAndJurisdictionAndIsActiveTrue(legalEntityId, jurisdiction);
        if (configs.isEmpty()) {
            throw new TaxConfigurationNotFoundException(legalEntityId, jurisdiction);
        }
        return configs.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public TaxConfigurationDto createTaxConfiguration(TaxConfigurationDto request) {
        // Resolve jurisdiction from the legal entity's country
        LegalEntity legalEntity = legalEntityRepository.findById(request.getLegalEntityId())
                .orElseThrow(() -> new PayrollProcessingException(
                        "Legal entity not found: " + request.getLegalEntityId()));
        Jurisdiction jurisdiction = toJurisdiction(legalEntity.getCountry());

        // Default taxType to "GENERAL" if not provided
        String taxType = request.getTaxType() != null ? request.getTaxType() : "GENERAL";

        // NEPAL: enforce one tax configuration per tax_type (allows multiple configs
        // for different tax_types, e.g., SSF + INCOME_TAX)
        if (jurisdiction == Jurisdiction.NEPAL) {
            List<TaxConfiguration> existing = repository.findByLegalEntityIdAndJurisdictionAndIsActiveTrue(
                    request.getLegalEntityId(), jurisdiction);
            boolean duplicateTaxType = existing.stream()
                    .anyMatch(e -> taxType.equals(e.getTaxType()));
            if (duplicateTaxType) {
                throw new InvalidTaxConfigurationException(
                        "Nepal entities can only have one tax configuration per tax_type. "
                                + "tax_type '" + taxType + "' already exists for this entity. "
                                + "Please update the existing config instead of creating a new one.");
            }
        }

        TaxConfiguration config = new TaxConfiguration();
        config.setLegalEntity(legalEntity);
        config.setTaxName(request.getTaxName());
        config.setTaxType(taxType);
        config.setJurisdiction(jurisdiction);
        config.setSsfEmployeeRate(request.getSsfEmployeeRate());
        config.setSsfEmployerRate(request.getSsfEmployerRate());
        config.setSsfMaxCapAmount(request.getSsfMaxCapAmount());
        config.setPfEmployeeRate(request.getPfEmployeeRate());
        config.setPfEmployerRate(request.getPfEmployerRate());
        config.setPfMaxCapAmount(request.getPfMaxCapAmount());
        // Unified flat-rate fields
        if (request.getFlatEmployeeRate() != null) config.setFlatEmployeeRate(request.getFlatEmployeeRate());
        if (request.getFlatEmployerRate() != null) config.setFlatEmployerRate(request.getFlatEmployerRate());
        if (request.getFlatCapAmount() != null) config.setFlatCapAmount(request.getFlatCapAmount());
        config.setProfessionalTaxAmount(request.getProfessionalTaxAmount());
        config.setProfessionalTaxState(request.getProfessionalTaxState());
        // effectiveFrom is documented as optional but is @NotNull on the entity —
        // default to today rather than letting a missing value reach the DB flush
        // as an uncaught ConstraintViolationException (500).
        config.setEffectiveFrom(request.getEffectiveFrom() != null
                ? request.getEffectiveFrom() : LocalDate.now());
        config.setEffectiveTo(request.getEffectiveTo());
        config.setIsActive(true);
        config.setStatus(Status.ACTIVE);
        if (request.getCalculationMethod() != null) config.setCalculationMethod(request.getCalculationMethod());

        if (request.getTaxSlabs() != null) {
            validateNoOverlappingSlabs(request.getTaxSlabs());
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
        if (request.getTaxName() != null) config.setTaxName(request.getTaxName());
        if (request.getTaxType() != null) config.setTaxType(request.getTaxType());
        if (request.getSsfEmployeeRate() != null) config.setSsfEmployeeRate(request.getSsfEmployeeRate());
        if (request.getSsfEmployerRate() != null) config.setSsfEmployerRate(request.getSsfEmployerRate());
        if (request.getSsfMaxCapAmount() != null) config.setSsfMaxCapAmount(request.getSsfMaxCapAmount());
        if (request.getPfEmployeeRate() != null) config.setPfEmployeeRate(request.getPfEmployeeRate());
        if (request.getPfEmployerRate() != null) config.setPfEmployerRate(request.getPfEmployerRate());
        if (request.getPfMaxCapAmount() != null) config.setPfMaxCapAmount(request.getPfMaxCapAmount());
        // Unified flat-rate fields
        if (request.getFlatEmployeeRate() != null) config.setFlatEmployeeRate(request.getFlatEmployeeRate());
        if (request.getFlatEmployerRate() != null) config.setFlatEmployerRate(request.getFlatEmployerRate());
        if (request.getFlatCapAmount() != null) config.setFlatCapAmount(request.getFlatCapAmount());
        if (request.getProfessionalTaxAmount() != null) config.setProfessionalTaxAmount(request.getProfessionalTaxAmount());
        if (request.getProfessionalTaxState() != null) config.setProfessionalTaxState(request.getProfessionalTaxState());
        if (request.getEffectiveFrom() != null) config.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) config.setEffectiveTo(request.getEffectiveTo());
        if (request.getIsActive() != null) config.setIsActive(request.getIsActive());
        if (request.getCalculationMethod() != null) config.setCalculationMethod(request.getCalculationMethod());

        // Delete existing tax slabs explicitly to avoid Hibernate flush-order issues
        // where INSERTS (new slabs) are executed before DELETES (orphaned slabs),
        // violating the unique constraint uk_ts_config_order (tax_configuration_id, slab_order).
        if (request.getTaxSlabs() != null) {
            validateNoOverlappingSlabs(request.getTaxSlabs());

            List<TaxSlab> existingSlabs = taxSlabRepository
                    .findByTaxConfigurationIdOrderBySlabOrderAsc(config.getId());
            if (!existingSlabs.isEmpty()) {
                taxSlabRepository.deleteAll(existingSlabs);
                taxSlabRepository.flush();
            }

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
    @Transactional(readOnly = true)
    public List<TaxConfigurationDto> listAllTaxConfigurations() {
        return repository.findAll().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaxConfigurationDto> listTaxConfigurationsByEntity(UUID legalEntityId) {
        return repository.findByLegalEntityIdAndIsActiveTrue(legalEntityId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Validates that tax slabs do not have overlapping income ranges.
     * Slabs are sorted by incomeFrom and each slab's incomeTo must not overlap
     * with the next slab's incomeFrom.
     *
     * @param slabs the list of tax slab DTOs to validate
     * @throws InvalidTaxConfigurationException if overlapping ranges are detected
     */
    private void validateNoOverlappingSlabs(List<TaxSlabDto> slabs) {
        if (slabs.size() < 2) {
            return;
        }

        // Sort by incomeFrom to detect overlaps
        List<TaxSlabDto> sorted = slabs.stream()
                .sorted(java.util.Comparator.comparing(TaxSlabDto::getIncomeFrom))
                .toList();

        for (int i = 0; i < sorted.size() - 1; i++) {
            TaxSlabDto current = sorted.get(i);
            TaxSlabDto next = sorted.get(i + 1);

            // A null incomeTo means "unlimited" — any subsequent slab would overlap
            if (current.getIncomeTo() == null
                    || next.getIncomeFrom().compareTo(current.getIncomeTo()) < 0) {
                throw new InvalidTaxConfigurationException(
                        "Tax slabs have overlapping income ranges: slab '" + current.getIncomeFrom()
                                + "-" + (current.getIncomeTo() != null ? current.getIncomeTo() : "∞")
                                + "' overlaps with slab '" + next.getIncomeFrom()
                                + "-" + (next.getIncomeTo() != null ? next.getIncomeTo() : "∞") + "'");
            }
        }
    }

    private TaxConfigurationDto toDto(TaxConfiguration entity) {
        return TaxConfigurationDto.builder()
                .id(entity.getId())
                .legalEntityId(entity.getLegalEntity() != null ? entity.getLegalEntity().getId() : null)
                .legalEntityName(entity.getLegalEntity() != null ? entity.getLegalEntity().getEntityName() : null)
                .taxName(entity.getTaxName())
                .taxType(entity.getTaxType())
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
                .calculationMethod(entity.getCalculationMethod())
                .flatEmployeeRate(entity.getFlatEmployeeRate())
                .flatEmployerRate(entity.getFlatEmployerRate())
                .flatCapAmount(entity.getFlatCapAmount())
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
