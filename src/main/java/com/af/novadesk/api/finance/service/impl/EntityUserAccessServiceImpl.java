package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.DuplicateUserAccessException;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.ShadowUserNotFoundException;
import com.af.novadesk.api.finance.exception.UserAccessNotFoundException;
import com.af.novadesk.api.finance.mapper.EntityUserAccessMapper;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.EntityAccessEmailPublisher;
import com.af.novadesk.api.finance.service.EntityUserAccessOutboxService;
import com.af.novadesk.api.finance.service.EntityUserAccessService;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link EntityUserAccessService}.
 * Manages user ↔ entity access grants and entity context switching (LLR-FIN-01.3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityUserAccessServiceImpl implements EntityUserAccessService {

    private final EntityUserAccessRepository   accessRepository;
    private final LegalEntityRepository        legalEntityRepository;
    private final ShadowUserRepository         shadowUserRepository;
    private final EntityUserAccessMapper        mapper;
    private final EntityUserAccessOutboxService  outboxService;
    private final FinanceSecurityContext        securityContext;
    private final EntityAccessEmailPublisher    emailPublisher;

    // =========================================================================
    // LLR-FIN-01.3: Grant Access
    // =========================================================================

    @Override
    @Transactional
    public EntityUserAccessDto grantAccess(UUID entityId, EntityUserAccessDto request) {
        LegalEntity entity = requireEntityInOrg(entityId);

        ShadowUser shadowUser = shadowUserRepository.findByAuthUserId(request.getAuthUserId())
                .orElseGet(() -> createShadowUserFromRequest(request));

        if (accessRepository.existsByShadowUserAuthUserIdAndLegalEntityId(
                request.getAuthUserId(), entityId)) {
            throw new DuplicateUserAccessException(request.getAuthUserId(), entityId);
        }

        EntityUserAccess access = EntityUserAccess.builder()
                .shadowUser(shadowUser)
                .legalEntity(entity)
                .entityRole(request.getEntityRole())
                .status(Status.ACTIVE)
                .build();

        EntityUserAccess saved = accessRepository.save(access);
        log.info("Granted role '{}' to user {} on entity {}", request.getEntityRole(),
                request.getAuthUserId(), entityId);

        outboxService.publishAccessGranted(saved, securityContext.getAuthUserId(),
                securityContext.getOrganizationId());

        // Publish entity-access-granted email via RabbitMQ → af-notification (mailer)
        String loginLink = String.format("%s/login?entityId=%s",
                System.getProperty("app.frontend-base-url", "https://novadesk.auroraforge.co"),
                entityId);
        String displayName = shadowUser.getDisplayName() != null
                ? shadowUser.getDisplayName()
                : shadowUser.getEmail();
        String orgIdentifier = entity.getOrganizationId() != null
                ? entity.getOrganizationId().toString()
                : "your organisation";
        emailPublisher.publishEntityAccessGrantedEmail(
                shadowUser.getEmail(),
                displayName,
                entity.getEntityName(),
                orgIdentifier,
                request.getEntityRole(),
                loginLink
        );

        return mapper.toDto(saved);
    }

    // =========================================================================
    // LLR-FIN-01.3: Revoke Access
    // =========================================================================

    @Override
    @Transactional
    public void revokeAccess(UUID entityId, UUID accessId) {
        requireEntityInOrg(entityId);   // org-level gate — must match before touching any access record
        EntityUserAccess access = accessRepository.findById(accessId)
                .filter(a -> a.getLegalEntity().getId().equals(entityId))
                .orElseThrow(() -> new UserAccessNotFoundException(accessId));

        access.setStatus(Status.INACTIVE);
        accessRepository.save(access);
        log.info("Revoked access {} on entity {}", accessId, entityId);

        outboxService.publishAccessRevoked(access, securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
    }

    // =========================================================================
    // LLR-FIN-01.3: Update Role
    // =========================================================================

    @Override
    @Transactional
    public EntityUserAccessDto updateRole(UUID entityId, UUID accessId,
                                          EntityUserAccessDto request) {
        EntityUserAccess access = accessRepository.findById(accessId)
                .filter(a -> a.getLegalEntity().getId().equals(entityId))
                .orElseThrow(() -> new UserAccessNotFoundException(accessId));

        String previousRole = access.getEntityRole();
        access.setEntityRole(request.getEntityRole());
        EntityUserAccess saved = accessRepository.save(access);
        log.info("Role updated {} → {} for access {}", previousRole, request.getEntityRole(), accessId);

        outboxService.publishRoleChanged(saved, previousRole, request.getEntityRole(),
                securityContext.getAuthUserId(),
                securityContext.getOrganizationId());
        return mapper.toDto(saved);
    }

    // =========================================================================
    // LLR-FIN-01.3: Entity Context Switch
    // =========================================================================

    @Override
    @Transactional
    public EntityContextDto selectEntityContext(EntityContextDto request) {
        UUID authUserId = securityContext.getAuthUserId();
        UUID entityId   = request.getLegalEntityId();

        if (!accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                Status.ACTIVE, authUserId, entityId)) {
            throw new EntityAccessDeniedException(authUserId, entityId);
        }

        LegalEntity entity = requireEntityInOrg(entityId);  // verify org membership before any write

        LocalDateTime now = LocalDateTime.now();
        accessRepository.updateLastAccessedAt(authUserId, entityId, now);
        log.info("User {} switched context to entity {}", authUserId, entityId);

        EntityContextDto ctx = mapper.toContextDto(entity);
        ctx.setSelectedAt(now);
        return ctx;
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @Override
    public List<EntityUserAccessDto> listAccessForEntity(UUID entityId) {
        requireEntityInOrg(entityId);
        return mapper.toAccessDtoList(accessRepository.findAllByLegalEntityId(entityId));
    }

    @Override
    public List<LegalEntitySummaryDto> listAccessibleEntities() {
        UUID authUserId = securityContext.getAuthUserId();
        // ORG_ADMIN, SYSTEM_ADMIN, and SUPER_ADMIN can see ALL entities in the org
        List<String> roles = securityContext.getRoles();
        boolean isPrivileged = roles.stream().anyMatch(r ->
                "ORG_ADMIN".equalsIgnoreCase(r)
                || "SYSTEM_ADMIN".equalsIgnoreCase(r)
                || "SUPER_ADMIN".equalsIgnoreCase(r));
        if (isPrivileged) {
            UUID orgId = securityContext.getOrganizationId();
            return mapper.toSummaryDtoList(legalEntityRepository.findAllByOrganizationId(orgId));
        }
        return mapper.toSummaryDtoList(legalEntityRepository.findAccessibleByAuthUserId(authUserId));
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
     * Materialises the shadow projection for a user who has never authenticated
     * against this service (the JWT filter only upserts shadow users on login,
     * and no user.created consumer exists). Mirrors the employee onboarding
     * flow in EmployeeServiceImpl. Requires the caller to supply the email;
     * without it the original not-found semantics are preserved.
     */
    private ShadowUser createShadowUserFromRequest(EntityUserAccessDto request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ShadowUserNotFoundException(request.getAuthUserId());
        }
        ShadowUser created = ShadowUser.builder()
                .authUserId(request.getAuthUserId())
                .organizationId(securityContext.getOrganizationId())
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .lastSyncedAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .build();
        ShadowUser saved = shadowUserRepository.save(created);
        log.info("ShadowUser created on-demand for entity access grant: authUserId={}, email={}",
                request.getAuthUserId(), request.getEmail());
        return saved;
    }
}
