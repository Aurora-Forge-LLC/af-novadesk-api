package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.*;
import com.af.novadesk.api.finance.mapper.LegalEntityMapper;
import com.af.novadesk.api.finance.mapper.FiscalYearSettingMapper;
import com.af.novadesk.api.finance.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.finance.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core service for Multi-Entity Management (LLR-FIN-01).
 *
 * <p>Orchestrates entity creation, approval/rejection lifecycle, status management,
 * and publishes domain events to the transactional outbox — all within a single
 * database transaction per operation.</p>
 *
 * <p>Organisation scoping: every query is automatically filtered by
 * so cross-org data leakage
 * is impossible at the service layer.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalEntityService {

    private final LegalEntityRepository         legalEntityRepository;
    private final FiscalYearSettingRepository   fiscalYearSettingRepository;
    private final LegalEntityMapper             mapper;
    private final FiscalYearSettingMapper       fiscalYearSettingMapper;
    private final FiscalYearTemplateService     fiscalYearTemplateService;
    private final ChartOfAccountTemplateService chartOfAccountTemplateService;
    private final BankAccountTemplateService    bankAccountTemplateService;
    private final LegalEntityOutboxService      outboxService;
    private final FinanceSecurityContext        securityContext;

    // =========================================================================
    // LLR-FIN-01.1: Entity Creation
    // =========================================================================

    /**
     * Creates a new LegalEntity in PENDING approval state.
     * Publishes {@code LEGAL_ENTITY_CREATED} event to the outbox.
     */
    @Transactional
    public LegalEntityDto createLegalEntity(LegalEntityDto request) {
        UUID orgId = securityContext.getOrganizationId();

        // Uniqueness guards (LLR-FIN-01.1)
        // Check within org scope first
        if (legalEntityRepository.existsByEntityNameAndOrganizationId(request.getEntityName(), orgId)) {
            throw new DuplicateEntityException("name", request.getEntityName());
        }
        if (legalEntityRepository.existsByEntityCodeAndOrganizationId(request.getEntityCode(), orgId)) {
            throw new DuplicateEntityException("code", request.getEntityCode());
        }
        // Also check globally because the DB constraints (uk_legal_entity_name, uk_legal_entity_code)
        // are global (not scoped to organization_id). This prevents cross-org collisions.
        if (legalEntityRepository.existsByEntityName(request.getEntityName())) {
            throw new DuplicateEntityException("name", request.getEntityName());
        }
        if (legalEntityRepository.existsByEntityCode(request.getEntityCode())) {
            throw new DuplicateEntityException("code", request.getEntityCode());
        }

        LegalEntity entity = mapper.toEntity(request);
        entity.setOrganizationId(orgId);
        entity.setBaseCurrency(request.getCountry().getDefaultCurrencyCode());
        entity.setApprovalStatus(ApprovalStatus.PENDING);
        entity.setStatus(Status.ACTIVE);

        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Created legal entity '{}' (id={}) for org={}", saved.getEntityName(), saved.getId(), orgId);

        // Publish outbox event in same transaction (LLR-FIN-01.2 notification trigger)
        outboxService.publishEntityCreated(saved, securityContext.getAuthUserId(), orgId);

        return mapper.toDto(saved);
    }

    // =========================================================================
    // LLR-FIN-01.2: Entity Activation
    // =========================================================================

    /**
     * Approves a pending entity. Within the same transaction:
     * 1. Transitions ApprovalStatus → APPROVED
     * 2. Seeds Chart of Accounts from country template
     * 3. Seeds default bank accounts
     * 4. Initialises FiscalYearSetting from country template (or override)
     * 5. Publishes LEGAL_ENTITY_APPROVED outbox event
     */
    @Transactional
    public LegalEntityDto approveEntity(UUID entityId, ApproveEntityDto request) {
        LegalEntity entity = requireEntityInOrg(entityId);

        if (entity.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new InvalidEntityStateException(
                    entityId, entity.getApprovalStatus().name(), "approve");
        }

        entity.setApprovalStatus(ApprovalStatus.APPROVED);

        // Seed Chart of Accounts from country template (LLR-FIN-01.2)
        List<ChartOfAccount> coaDefaults = chartOfAccountTemplateService.buildFromCountry(entity.getCountry());
        entity.getChartOfAccounts().clear();
        for (ChartOfAccount account : coaDefaults) {
            account.setLegalEntity(entity);
            entity.getChartOfAccounts().add(account);
        }

        // Seed default bank accounts from country template (LLR-FIN-01.2)
        List<EntityBankAccount> bankAccountDefaults = bankAccountTemplateService.buildFromCountry(entity.getCountry());
        entity.getBankAccounts().clear();
        for (EntityBankAccount account : bankAccountDefaults) {
            account.setLegalEntity(entity);
            entity.getBankAccounts().add(account);
        }

        // Init fiscal year settings (LLR-FIN-01.2)
        FiscalYearSetting fiscalYear = (request.getFiscalYearOverride() != null)
                ? mapper.toEntity(request.getFiscalYearOverride())
                : fiscalYearTemplateService.buildFromCountry(entity.getCountry());
        if (fiscalYear != null) {
            fiscalYear.setLegalEntity(entity);
            fiscalYearSettingRepository.save(fiscalYear);
        }

        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Approved legal entity id={}", entityId);

        outboxService.publishEntityApproved(saved, securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    /**
     * Rejects a pending entity and publishes {@code LEGAL_ENTITY_REJECTED} event.
     */
    @Transactional
    public LegalEntityDto rejectEntity(UUID entityId, RejectEntityDto request) {
        LegalEntity entity = requireEntityInOrg(entityId);

        if (entity.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new InvalidEntityStateException(
                    entityId, entity.getApprovalStatus().name(), "reject");
        }

        entity.setApprovalStatus(ApprovalStatus.REJECTED);
        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Rejected legal entity id={}, reason='{}'", entityId, request.getReason());

        outboxService.publishEntityRejected(saved, request.getReason(),
                securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    /**
     * Toggles the operational Status (ACTIVE / INACTIVE) and publishes
     * {@code LEGAL_ENTITY_STATUS_CHANGED} event.
     */
    @Transactional
    public LegalEntityDto updateStatus(UUID entityId, LegalEntityDto request) {
        LegalEntity entity = requireEntityInOrg(entityId);
        Status previous = entity.getStatus();

        entity.setStatus(request.getStatus());
        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Legal entity id={} status changed {} \u2192 {}", entityId, previous, request.getStatus());

        outboxService.publishStatusChanged(saved, previous, request.getStatus(),
                securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public LegalEntityDto getById(UUID entityId) {
        return mapper.toDto(requireEntityInOrg(entityId));
    }

    public LegalEntityPageDto listAll(Pageable pageable) {
        UUID orgId = securityContext.getOrganizationId();
        Page<LegalEntity> page = legalEntityRepository.findAllByOrganizationId(orgId, pageable);
        return new LegalEntityPageDto(
                mapper.toSummaryDtoList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private LegalEntity requireEntityInOrg(UUID entityId) {
        return legalEntityRepository
                .findByIdAndOrganizationId(entityId, securityContext.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException(entityId));
    }
}
