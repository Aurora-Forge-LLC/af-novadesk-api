package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.Department;
import com.af.novadesk.api.common.service.AuthHubClientService;
import com.af.novadesk.api.department.repository.DepartmentRepository;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.EntityUserInviteRequest;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.exception.DuplicateUserAccessException;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.exception.EntityAdminAlreadyExistsException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.exception.EntityRoleNotPermittedException;
import com.af.novadesk.api.finance.exception.SelfEntityAdminActionException;
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
    private final AuthHubClientService          authHubClientService;
    private final DepartmentRepository          departmentRepository;

    // =========================================================================
    // LLR-FIN-01.3: Grant Access
    // =========================================================================

    @Override
    @Transactional
    public EntityUserAccessDto grantAccess(UUID entityId, EntityUserAccessDto request) {
        requireEntityManagementRights(entityId);
        LegalEntity entity = requireEntityInOrg(entityId);

        assertOrgAdminMayOnlyGrantEntityAdmin(entityId, request.getEntityRole());
        assertSingleEntityAdminSlot(entityId, request.getEntityRole(), null);
        validateDepartmentForEntity(request.getDepartmentId(), entityId);

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
                .departmentId(request.getDepartmentId())
                .status(Status.PENDING)
                .build();

        EntityUserAccess saved = accessRepository.save(access);
        log.info("Granted role '{}' to user {} on entity {}", request.getEntityRole(),
                request.getAuthUserId(), entityId);

        // Assign the role in AuthHub too — otherwise this grant is inert: JWT
        // permissions/roles only ever come from AuthHub's user_roles table,
        // so without this the entity-scope check would pass but every
        // hasAuthority(...) permission gate would still fail. Idempotent if
        // the user already holds this role (e.g. via inviteUser's earlier
        // createEntityUser call, which assigns it directly in AuthHub).
        authHubClientService.syncEntityRole(request.getAuthUserId(), null, request.getEntityRole());

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
    // One-call invite: AuthHub user provisioning + entity access grant
    // =========================================================================

    @Override
    @Transactional
    public EntityUserAccessDto inviteUser(UUID entityId, EntityUserInviteRequest request) {
        // Fail fast on missing rights or a bad entity before provisioning in AuthHub.
        requireEntityManagementRights(entityId);
        requireEntityInOrg(entityId);
        // Validate role restrictions up front too — AuthHub user provisioning
        // below is a remote call outside this transaction, so failing late
        // (inside grantAccess) would leave an orphaned identity there.
        assertOrgAdminMayOnlyGrantEntityAdmin(entityId, request.getEntityRole());
        assertSingleEntityAdminSlot(entityId, request.getEntityRole(), null);

        UUID authUserId = UUID.randomUUID();
        // Creates a passwordless (PENDING_SETUP) user with an EntityUser record
        // (no OrgUser membership — entity-scoped users do NOT appear in org_users).
        // Throws on duplicate email, in which case nothing is persisted locally.
        authHubClientService.createEntityUser(authUserId, request.getEmail(),
                request.getFirstName(), request.getLastName(),
                request.getEntityRole(),
                securityContext.getOrganizationId());

        EntityUserAccessDto grant = new EntityUserAccessDto();
        grant.setAuthUserId(authUserId);
        grant.setEntityRole(request.getEntityRole());
        grant.setDepartmentId(request.getDepartmentId());
        grant.setEmail(request.getEmail());
        grant.setDisplayName((request.getFirstName() + " " + request.getLastName()).trim());

        return grantAccess(entityId, grant);
    }

    // =========================================================================
    // LLR-FIN-01.3: Revoke Access
    // =========================================================================

    @Override
    @Transactional
    public void revokeAccess(UUID entityId, UUID accessId) {
        requireEntityManagementRights(entityId);
        requireEntityInOrg(entityId);   // org-level gate — must match before touching any access record
        EntityUserAccess access = accessRepository.findById(accessId)
                .filter(a -> a.getLegalEntity().getId().equals(entityId))
                .orElseThrow(() -> new UserAccessNotFoundException(accessId));

        assertOrgAdminMayOnlyTouchEntityAdminRecord(entityId, access.getEntityRole());
        assertNotSelfEntityAdminRemoval(access);

        access.setStatus(Status.INACTIVE);
        accessRepository.save(access);
        log.info("Revoked access {} on entity {}", accessId, entityId);

        // Keep AuthHub's JWT-sourced role assignment in step: if the user no
        // longer holds this role via any OTHER active grant, remove it from
        // AuthHub too. Without this, a revoked ENTITY_ADMIN (or any entity
        // role) keeps its permissions in every future JWT forever, since
        // AuthHub's user_roles table is never otherwise cleaned up.
        UUID authUserId = access.getShadowUser().getAuthUserId();
        boolean stillHeldElsewhere = accessRepository.existsByStatusAndShadowUserAuthUserIdAndEntityRole(
                Status.ACTIVE, authUserId, access.getEntityRole());
        authHubClientService.syncEntityRole(
                authUserId, stillHeldElsewhere ? null : access.getEntityRole(), null);

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
        requireEntityManagementRights(entityId);
        requireEntityInOrg(entityId);   // org-level gate — must match before touching any access record
        EntityUserAccess access = accessRepository.findById(accessId)
                .filter(a -> a.getLegalEntity().getId().equals(entityId))
                .orElseThrow(() -> new UserAccessNotFoundException(accessId));

        assertOrgAdminMayOnlyTouchEntityAdminRecord(entityId, access.getEntityRole());
        assertOrgAdminMayOnlyGrantEntityAdmin(entityId, request.getEntityRole());
        assertSingleEntityAdminSlot(entityId, request.getEntityRole(), accessId);
        assertNotSelfEntityAdminDemotion(access, request.getEntityRole());

        String previousRole = access.getEntityRole();
        access.setEntityRole(request.getEntityRole());
        EntityUserAccess saved = accessRepository.save(access);
        log.info("Role updated {} → {} for access {}", previousRole, request.getEntityRole(), accessId);

        // Keep AuthHub's JWT-sourced role assignment in step with this change.
        // Only drop the previous role from AuthHub if the user doesn't still
        // hold it via some OTHER active grant (they may legitimately need it
        // there too, e.g. ENTITY_ADMIN on a second entity).
        UUID authUserId = saved.getShadowUser().getAuthUserId();
        boolean previousStillHeldElsewhere = accessRepository
                .existsByStatusAndShadowUserAuthUserIdAndEntityRole(Status.ACTIVE, authUserId, previousRole);
        authHubClientService.syncEntityRole(
                authUserId,
                previousStillHeldElsewhere ? null : previousRole,
                saved.getEntityRole());

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

        if (!hasOrgWideEntityVisibility()) {
            // Check for ACTIVE grant first (already activated)
            boolean hasActive = accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                    Status.ACTIVE, authUserId, entityId);

            if (!hasActive) {
                // Check for PENDING grant — first-time activation
                boolean hasPending = accessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                        Status.PENDING, authUserId, entityId);
                if (hasPending) {
                    // Activate the PENDING grant on first context switch
                    accessRepository.updateStatusByAuthUserIdAndLegalEntityId(
                            Status.ACTIVE, authUserId, entityId);
                    log.info("Entity access activated: authUserId={}, entityId={}", authUserId, entityId);
                } else {
                    throw new EntityAccessDeniedException(authUserId, entityId);
                }
            }
        }

        LegalEntity entity = requireEntityInOrg(entityId);

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
        UUID orgId = securityContext.getOrganizationId();
        if (hasOrgWideEntityVisibility()) {
            return mapper.toSummaryDtoList(legalEntityRepository.findAllByOrganizationId(orgId));
        }
        return mapper.toSummaryDtoList(
                legalEntityRepository.findAccessibleByAuthUserIdAndOrganizationId(authUserId, orgId));
    }

    /**
     * Authorization gate for access-management operations (grant, invite,
     * update role, revoke). Allowed callers:
     * <ul>
     *   <li>ORG_ADMIN / SUPER_ADMIN via the JWT roles claim (org isolation is
     *       enforced separately by {@link #requireEntityInOrg(UUID)});</li>
     *   <li>a caller holding an ACTIVE ENTITY_ADMIN grant on this specific
     *       entity.</li>
     * </ul>
     * A global ENTITY_ADMIN JWT role deliberately does NOT qualify — entity
     * admin rights are per-entity grants, never org-wide.
     */
    private void requireEntityManagementRights(UUID entityId) {
        boolean orgTierAdmin = securityContext.getRoles().stream().anyMatch(r ->
                "ORG_ADMIN".equalsIgnoreCase(r) || "SUPER_ADMIN".equalsIgnoreCase(r));
        if (orgTierAdmin) {
            return;
        }

        UUID callerId = securityContext.getAuthUserId();
        boolean entityAdminHere = accessRepository
                .existsByStatusAndShadowUserAuthUserIdAndLegalEntityIdAndEntityRole(
                        Status.ACTIVE, callerId, entityId, "ENTITY_ADMIN");
        if (!entityAdminHere) {
            log.warn("Entity management denied: user {} is not ENTITY_ADMIN on entity {}", callerId, entityId);
            throw new EntityAccessDeniedException(callerId, entityId);
        }
    }

    /**
     * System-tier roles and org-tier roles (scope: "across org entities" —
     * see STEALTH_OS_ROLES_PERMISSIONS_QUICK_REFERENCE.md §1) see ALL entities
     * in their org without per-entity access grants; everyone else only sees
     * entities they hold an explicit ACTIVE grant for.
     */
    private boolean hasOrgWideEntityVisibility() {
        return securityContext.getRoles().stream().anyMatch(r ->
                "ORG_ADMIN".equalsIgnoreCase(r)
                || "ORG_HR".equalsIgnoreCase(r)
                || "ORG_IT".equalsIgnoreCase(r)
                || "ORG_FINANCE".equalsIgnoreCase(r)
                || "SYSTEM_ADMIN".equalsIgnoreCase(r)
                || "SUPER_ADMIN".equalsIgnoreCase(r));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static final String ENTITY_ADMIN_ROLE = "ENTITY_ADMIN";

    /** True if the caller holds ORG_ADMIN but not SUPER_ADMIN. */
    private boolean callerIsOrgAdminOnly() {
        List<String> roles = securityContext.getRoles();
        boolean isOrgAdmin = roles.stream().anyMatch("ORG_ADMIN"::equalsIgnoreCase);
        boolean isSuperAdmin = roles.stream().anyMatch("SUPER_ADMIN"::equalsIgnoreCase);
        return isOrgAdmin && !isSuperAdmin;
    }

    /**
     * ORG_ADMIN may only grant/reassign the ENTITY_ADMIN role — every other
     * entity-tier role (FINANCE_MANAGER, HR_MANAGER, IT_ADMIN, MANAGER,
     * ACCOUNTANT, EMPLOYEE) must be managed by that entity's own ENTITY_ADMIN.
     * No-op for SUPER_ADMIN or a caller acting under their own per-entity
     * ENTITY_ADMIN grant.
     */
    private void assertOrgAdminMayOnlyGrantEntityAdmin(UUID entityId, String requestedRole) {
        if (!callerIsOrgAdminOnly()) {
            return;
        }
        if (!ENTITY_ADMIN_ROLE.equalsIgnoreCase(requestedRole)) {
            throw new EntityRoleNotPermittedException(entityId, requestedRole);
        }
    }

    /**
     * ORG_ADMIN may only update/revoke access records that are currently
     * ENTITY_ADMIN grants — it cannot touch other entity-tier role holders.
     */
    private void assertOrgAdminMayOnlyTouchEntityAdminRecord(UUID entityId, String currentRole) {
        if (!callerIsOrgAdminOnly()) {
            return;
        }
        if (!ENTITY_ADMIN_ROLE.equalsIgnoreCase(currentRole)) {
            throw new EntityRoleNotPermittedException(entityId, currentRole);
        }
    }

    /**
     * At most one ACTIVE ENTITY_ADMIN per legal entity. Applies regardless of
     * caller tier — this is an absolute per-entity invariant, not just an
     * ORG_ADMIN restriction.
     */
    private void assertSingleEntityAdminSlot(UUID entityId, String requestedRole, UUID excludeAccessId) {
        if (!ENTITY_ADMIN_ROLE.equalsIgnoreCase(requestedRole)) {
            return;
        }
        boolean exists = excludeAccessId == null
                ? accessRepository.existsByStatusAndLegalEntityIdAndEntityRole(
                        Status.ACTIVE, entityId, ENTITY_ADMIN_ROLE)
                : accessRepository.existsByStatusAndLegalEntityIdAndEntityRoleAndIdNot(
                        Status.ACTIVE, entityId, ENTITY_ADMIN_ROLE, excludeAccessId);
        if (exists) {
            throw new EntityAdminAlreadyExistsException(entityId);
        }
    }

    /** The entity's ENTITY_ADMIN cannot revoke their own access grant. */
    private void assertNotSelfEntityAdminRemoval(EntityUserAccess access) {
        boolean isSelf = access.getShadowUser().getAuthUserId().equals(securityContext.getAuthUserId());
        boolean isEntityAdmin = ENTITY_ADMIN_ROLE.equalsIgnoreCase(access.getEntityRole());
        if (isSelf && isEntityAdmin) {
            throw new SelfEntityAdminActionException(access.getLegalEntity().getId());
        }
    }

    /** The entity's ENTITY_ADMIN cannot change their own role away from ENTITY_ADMIN. */
    private void assertNotSelfEntityAdminDemotion(EntityUserAccess access, String requestedRole) {
        boolean isSelf = access.getShadowUser().getAuthUserId().equals(securityContext.getAuthUserId());
        boolean currentlyEntityAdmin = ENTITY_ADMIN_ROLE.equalsIgnoreCase(access.getEntityRole());
        boolean demoting = !ENTITY_ADMIN_ROLE.equalsIgnoreCase(requestedRole);
        if (isSelf && currentlyEntityAdmin && demoting) {
            throw new SelfEntityAdminActionException(access.getLegalEntity().getId());
        }
    }

    private LegalEntity requireEntityInOrg(UUID entityId) {
        return legalEntityRepository
                .findByIdAndOrganizationId(entityId, securityContext.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException(entityId));
    }

    /**
     * Validates that {@code departmentId}, when provided, refers to a
     * department actually scoped to {@code legalEntityId}. Null is allowed
     * (e.g. for first-user / initial admin setup where no departments exist yet).
     */
    private void validateDepartmentForEntity(UUID departmentId, UUID legalEntityId) {
        if (departmentId == null) {
            return;
        }
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
        if (!legalEntityId.equals(department.getLegalEntityId())) {
            throw new IllegalArgumentException("Department does not belong to the target legal entity");
        }
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
