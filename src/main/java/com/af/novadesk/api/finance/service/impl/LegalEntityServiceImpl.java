package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.dto.UpdateEntityStatusRequest;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.*;
import com.af.novadesk.api.finance.mapper.FiscalYearSettingMapper;
import com.af.novadesk.api.finance.mapper.LegalEntityMapper;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.common.repository.FiscalYearSettingRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.EntityAccessGuard;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.AccountTemplateService;
import com.af.novadesk.api.finance.service.BankAccountTemplateService;
import com.af.novadesk.api.finance.service.ChartOfAccountTemplateService;
import com.af.novadesk.api.finance.service.FiscalYearTemplateService;
import com.af.novadesk.api.finance.service.LegalEntityOutboxService;
import com.af.novadesk.api.finance.service.LegalEntityService;
import com.af.novadesk.api.department.service.DepartmentService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link LegalEntityService}.
 *
 * <p>Orchestrates entity creation, approval/rejection lifecycle, status management,
 * and publishes domain events to the transactional outbox — all within a single
 * database transaction per operation.</p>
 *
 * <p>Organisation scoping: every query is automatically filtered by
 * so cross-org data leakage is impossible at the service layer.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LegalEntityServiceImpl implements LegalEntityService {

    private final LegalEntityRepository         legalEntityRepository;
    private final FiscalYearSettingRepository   fiscalYearSettingRepository;
    private final LegalEntityMapper             mapper;
    private final FiscalYearSettingMapper       fiscalYearSettingMapper;
    private final FiscalYearTemplateService     fiscalYearTemplateService;
    private final ChartOfAccountTemplateService chartOfAccountTemplateService;
    private final BankAccountTemplateService    bankAccountTemplateService;
    private final AccountTemplateService        accountTemplateService;
    private final AccountRepository             accountRepository;
    private final LegalEntityOutboxService      outboxService;
    private final FinanceSecurityContext        securityContext;
    private final EntityAccessGuard             entityAccessGuard;
    private final ShadowUserRepository          shadowUserRepository;
    private final EntityUserAccessRepository    entityUserAccessRepository;
    private final DepartmentService             departmentService;

    // =========================================================================
    // LLR-FIN-01.1: Entity Creation
    // =========================================================================

    /**
     * Creates a new LegalEntity in PENDING approval state.
     * Publishes {@code LEGAL_ENTITY_CREATED} event to the outbox.
     */
    @Override
    @Transactional
    public LegalEntityDto createLegalEntity(LegalEntityDto request) {
        requireOrgTierAdmin("create");
        UUID orgId = securityContext.getOrganizationId();

        // Uniqueness guards (LLR-FIN-01.1)
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

        // Auto-grant ADMIN access to the entity creator (LLR-FIN-01.3)
        UUID creatorAuthUserId = securityContext.getAuthUserId();
        ShadowUser creator = shadowUserRepository.findByAuthUserId(creatorAuthUserId)
                .orElseThrow(() -> new ShadowUserNotFoundException(creatorAuthUserId));
        EntityUserAccess creatorAccess = EntityUserAccess.builder()
                .shadowUser(creator)
                .legalEntity(saved)
                .entityRole("ADMIN")
                .status(Status.ACTIVE)
                .build();
        entityUserAccessRepository.save(creatorAccess);
        log.info("Auto-granted ADMIN access to creator authUserId={} for entity id={}",
                creatorAuthUserId, saved.getId());

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
     * 5. Seeds default departments (IT/HR/Finance) for the entity, and for the
     *    organization too if it has none yet
     * 6. Publishes LEGAL_ENTITY_APPROVED outbox event
     */
    @Override
    @Transactional
    public LegalEntityDto approveEntity(UUID entityId, ApproveEntityDto request) {
        requireOrgTierAdmin("approve");
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

        // Seed funding accounts (fa_accounts) from country template (LLR-FIN-02.1)
        List<Account> fundingAccounts = accountTemplateService.buildFromCountry(entity.getCountry(), entity);
        accountRepository.saveAll(fundingAccounts);
        log.info("Seeded {} funding accounts for entity id={}", fundingAccounts.size(), entityId);

        // Init fiscal year settings (LLR-FIN-01.2)
        FiscalYearSetting fiscalYear = (request.getFiscalYearOverride() != null)
                ? mapper.toEntity(request.getFiscalYearOverride())
                : fiscalYearTemplateService.buildFromCountry(entity.getCountry());
        if (fiscalYear != null) {
            fiscalYear.setLegalEntity(entity);
            FiscalYearSetting savedFiscalYear = fiscalYearSettingRepository.save(fiscalYear);
            // Set the managed reference back on the entity so the approve response
            // includes fiscalYearSetting instead of returning null.
            entity.setFiscalYearSetting(savedFiscalYear);
        }

        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Approved legal entity id={}", entityId);

        // Seed default departments (LLR-FIN-01.2) — org-level defaults first
        // (only if this org has none yet), then this entity's own defaults.
        departmentService.seedDefaultsForOrganization(saved.getOrganizationId());
        departmentService.seedDefaultsForEntity(saved.getOrganizationId(), saved.getId());

        outboxService.publishEntityApproved(saved, securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    /**
     * Rejects a pending entity and publishes {@code LEGAL_ENTITY_REJECTED} event.
     */
    @Override
    @Transactional
    public LegalEntityDto rejectEntity(UUID entityId, RejectEntityDto request) {
        requireOrgTierAdmin("reject");
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
    @Override
    @Transactional
    public LegalEntityDto updateStatus(UUID entityId, UpdateEntityStatusRequest request) {
        requireOrgTierAdmin("change the status of");
        LegalEntity entity = requireEntityInOrg(entityId);
        Status previous = entity.getStatus();

        entity.setStatus(request.getStatus());
        LegalEntity saved = legalEntityRepository.save(entity);
        log.info("Legal entity id={} status changed {} → {}", entityId, previous, request.getStatus());

        outboxService.publishStatusChanged(saved, previous, request.getStatus(),
                securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @Override
    public LegalEntityDto getById(UUID entityId) {
        LegalEntity entity = requireEntityInOrg(entityId);
        // Entity-scope guard: an entity-tier caller (e.g. ENTITY_ADMIN) may only
        // view an entity they hold a grant on; org-wide roles see any entity.
        entityAccessGuard.assertCanAccessEntity(entity.getId());
        return mapper.toDto(entity);
    }

    @Override
    public LegalEntityPageDto listAll(Pageable pageable) {
        UUID orgId = securityContext.getOrganizationId();

        // Org-wide roles see every entity in the organization (paged).
        if (entityAccessGuard.hasOrgWideVisibility()) {
            Page<LegalEntity> page = legalEntityRepository.findAllByOrganizationId(orgId, pageable);
            return new LegalEntityPageDto(
                    mapper.toSummaryDtoList(page.getContent()),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }

        // Entity-tier callers only see entities they hold an ACTIVE grant on.
        java.util.Set<UUID> accessibleIds = entityAccessGuard.accessibleEntityIds();
        List<LegalEntity> entities = accessibleIds.isEmpty()
                ? List.of()
                : legalEntityRepository.findAllById(accessibleIds).stream()
                        .filter(e -> orgId.equals(e.getOrganizationId()))
                        .collect(java.util.stream.Collectors.toList());
        return new LegalEntityPageDto(
                mapper.toSummaryDtoList(entities),
                0,
                entities.size(),
                entities.size(),
                entities.isEmpty() ? 0 : 1
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

    /**
     * Creating, approving, rejecting, or changing the status of a legal entity is
     * an org-tier action, restricted to ORG_ADMIN/SUPER_ADMIN/SYSTEM_ADMIN.
     *
     * <p>The controller only checks {@code organizations:write}, which ENTITY_ADMIN
     * also holds (needed for the entity-user-invite flow, which reuses that same
     * permission server-to-server). Without this explicit role gate, any
     * ENTITY_ADMIN could create/approve/reject/deactivate legal entities across
     * the whole org — an entity-tier role reaching into org-tier territory.</p>
     */
    private void requireOrgTierAdmin(String action) {
        boolean orgTierAdmin = securityContext.getRoles().stream().anyMatch(r ->
                "ORG_ADMIN".equalsIgnoreCase(r)
                || "SUPER_ADMIN".equalsIgnoreCase(r)
                || "SYSTEM_ADMIN".equalsIgnoreCase(r));
        if (!orgTierAdmin) {
            throw new LegalEntityManagementNotPermittedException(action);
        }
    }
}
