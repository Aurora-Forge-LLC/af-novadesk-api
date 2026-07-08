package com.af.novadesk.api.finance.security;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.repository.CmEmployeeEntityAssignmentRepository;
import com.af.novadesk.api.finance.exception.EntityAccessDeniedException;
import com.af.novadesk.api.finance.repository.EntityUserAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Central entity-scoping authorization guard.
 *
 * <p>Every module in this service already gates endpoints by <em>permission</em>
 * (via {@code @PreAuthorize("hasAuthority('...')")}) — that answers "may this
 * <b>kind</b> of user perform this <b>kind</b> of action". This guard answers the
 * orthogonal, per-request question: "may this specific caller act on <b>this
 * specific legal entity</b>".</p>
 *
 * <p><b>Policy</b> (single source of truth for the whole codebase):</p>
 * <ul>
 *   <li><b>Org-wide roles</b> — {@code SUPER_ADMIN}, {@code SYSTEM_ADMIN},
 *       {@code ORG_ADMIN}, {@code ORG_HR}, {@code ORG_IT}, {@code ORG_FINANCE} —
 *       operate across every entity in their organization and need no per-entity
 *       grant.</li>
 *   <li><b>Entity-tier grant holders</b> — users with an active
 *       {@link com.af.novadesk.api.finance.entity.EntityUserAccess} record
 *       on the target entity (invited/granted users like ENTITY_ADMIN,
 *       FINANCE_MANAGER, etc.).</li>
 *   <li><b>Employee assignment</b> — users who are onboarded employees with an
 *       active {@link com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment}
 *       on the target entity (implicit access via their employment).</li>
 * </ul>
 *
 * <p>The guard reads the JWT directly from the {@link SecurityContextHolder} so it
 * is usable from any layer regardless of which typed security-context wrapper
 * ({@code FinanceSecurityContext} / {@code IdentitySecurityContext}) that layer
 * otherwise uses.</p>
 */
@Component
@RequiredArgsConstructor
public class EntityAccessGuard {

    /**
     * Roles that see and act across every entity in their organization without a
     * per-entity access grant. Kept in sync with
     * {@code EntityUserAccessServiceImpl.hasOrgWideEntityVisibility()}.
     */
    private static final Set<String> ORG_WIDE_ROLES = Set.of(
            "SUPER_ADMIN", "SYSTEM_ADMIN",
            "ORG_ADMIN", "ORG_HR", "ORG_IT", "ORG_FINANCE");

    private final EntityUserAccessRepository           entityUserAccessRepository;
    private final CmEmployeeEntityAssignmentRepository employeeAssignmentRepository;

    /**
     * True when the caller holds a role that grants organization-wide entity
     * visibility (and therefore needs no per-entity grant).
     */
    public boolean hasOrgWideVisibility() {
        return currentRoles().stream()
                .anyMatch(r -> ORG_WIDE_ROLES.contains(r == null ? null : r.toUpperCase()));
    }

    /**
     * Asserts the caller may act on {@code legalEntityId}.
     * <p>
     * Access is granted if any of these hold:
     * <ol>
     *   <li>The caller holds an org-wide role (ORG_ADMIN, etc.)</li>
     *   <li>The caller has an ACTIVE {@code EntityUserAccess} grant on the entity</li>
     *   <li>The caller is an onboarded employee with an ACTIVE
     *       {@code CmEmployeeEntityAssignment} on the entity</li>
     * </ol>
     *
     * @throws EntityAccessDeniedException if none of the above conditions are met
     * @throws IllegalArgumentException if {@code legalEntityId} is null
     */
    public void assertCanAccessEntity(UUID legalEntityId) {
        if (legalEntityId == null) {
            throw new IllegalArgumentException(
                    "legalEntityId must not be null for an entity-scoped access check");
        }
        if (hasOrgWideVisibility()) {
            return;
        }
        UUID authUserId = currentAuthUserId();

        // Check 1: explicit EntityUserAccess grant (invited/granted users)
        if (entityUserAccessRepository.existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                Status.ACTIVE, authUserId, legalEntityId)) {
            return;
        }

        // Check 2: implicit employee assignment (onboarded employees)
        if (employeeAssignmentRepository
                .existsByEmployeeAuthUserIdAndLegalEntityIdAndAssignmentStatus(
                        authUserId, legalEntityId, EmployeeAssignmentStatus.ACTIVE)) {
            return;
        }

        throw new EntityAccessDeniedException(authUserId, legalEntityId);
    }

    /**
     * Asserts the caller may act on <b>at least one</b> of the supplied entities.
     * Used for operations keyed by a record (e.g. an employee) that may span
     * multiple legal entities.
     *
     * @throws EntityAccessDeniedException if the caller has access to none of them
     * @throws IllegalArgumentException if the collection is null/empty
     */
    public void assertCanAccessAnyOf(Collection<UUID> legalEntityIds) {
        if (legalEntityIds == null || legalEntityIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "legalEntityIds must not be empty for an entity-scoped access check");
        }
        if (hasOrgWideVisibility()) {
            return;
        }
        UUID authUserId = currentAuthUserId();
        boolean any = legalEntityIds.stream()
                .filter(java.util.Objects::nonNull)
                .anyMatch(id ->
                        entityUserAccessRepository
                                .existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
                                        Status.ACTIVE, authUserId, id)
                        || employeeAssignmentRepository
                                .existsByEmployeeAuthUserIdAndLegalEntityIdAndAssignmentStatus(
                                        authUserId, id, EmployeeAssignmentStatus.ACTIVE));
        if (!any) {
            throw new EntityAccessDeniedException(authUserId,
                    legalEntityIds.iterator().next());
        }
    }

    /**
     * The set of entity ids the caller holds an ACTIVE grant or assignment on.
     * Intended for filtering list results for non-org-wide callers.
     */
    public Set<UUID> accessibleEntityIds() {
        UUID authUserId = currentAuthUserId();

        // Collect from EntityUserAccess grants
        Set<UUID> ids = entityUserAccessRepository
                .findAllByShadowUserAuthUserId(authUserId).stream()
                .filter(a -> a.getStatus() == Status.ACTIVE)
                .map(a -> a.getLegalEntity().getId())
                .collect(Collectors.toSet());

        // Also collect from employee assignments
        employeeAssignmentRepository
                .findAllByEmployeeAuthUserId(authUserId).stream()
                .filter(a -> a.getAssignmentStatus() == EmployeeAssignmentStatus.ACTIVE)
                .map(a -> a.getLegalEntity().getId())
                .forEach(ids::add);

        return ids;
    }

    // -------------------------------------------------------------------------
    // JWT access (self-contained so the guard is context-wrapper agnostic)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<String> currentRoles() {
        Jwt jwt = jwt();
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null ? roles : List.of();
    }

    private UUID currentAuthUserId() {
        try {
            return UUID.fromString(jwt().getSubject());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT 'sub' claim is not a valid UUID", e);
        }
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "No authenticated JWT principal found in SecurityContext");
        }
        return jwt;
    }
}
