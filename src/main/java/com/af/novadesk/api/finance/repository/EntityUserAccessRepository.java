package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link EntityUserAccess} entity.
 */
@Repository
public interface EntityUserAccessRepository extends JpaRepository<EntityUserAccess, UUID> {

    List<EntityUserAccess> findAllByLegalEntityId(UUID legalEntityId);

    /** Count of access grants referencing a given department — used for delete validation. */
    long countByDepartmentId(UUID departmentId);

    List<EntityUserAccess> findAllByShadowUserAuthUserId(UUID authUserId);

    /** LLR-FIN-01.3: duplicate access check before granting. */
    boolean existsByShadowUserAuthUserIdAndLegalEntityId(UUID authUserId, UUID legalEntityId);

    /** LLR-FIN-01.3: access check that filters by status — used in context switch
     *  to prevent revoked (INACTIVE) access records from passing the check. */
    boolean existsByStatusAndShadowUserAuthUserIdAndLegalEntityId(
            Status status, UUID authUserId, UUID legalEntityId);

    /** Per-entity admin check: caller holds an ACTIVE grant with the given role
     *  on this specific entity — used to authorize access-management operations. */
    boolean existsByStatusAndShadowUserAuthUserIdAndLegalEntityIdAndEntityRole(
            Status status, UUID authUserId, UUID legalEntityId, String entityRole);

    /** Cross-entity check: does this user hold the given role via ANY ACTIVE
     *  grant (on any entity)? Used when revoking or changing a grant to decide
     *  whether the corresponding role should also be removed from AuthHub's
     *  global role assignment — see AuthHubClientService.syncEntityRole. */
    boolean existsByStatusAndShadowUserAuthUserIdAndEntityRole(
            Status status, UUID authUserId, String entityRole);

    /** Single-entity-admin-slot check: is there already an ACTIVE holder of the
     *  given role (e.g. ENTITY_ADMIN) on this entity, from any user? */
    boolean existsByStatusAndLegalEntityIdAndEntityRole(
            Status status, UUID legalEntityId, String entityRole);

    /** Same as above, excluding a specific access record — used when updating an
     *  existing grant so the record being changed doesn't collide with itself. */
    boolean existsByStatusAndLegalEntityIdAndEntityRoleAndIdNot(
            Status status, UUID legalEntityId, String entityRole, UUID excludeAccessId);

    Optional<EntityUserAccess> findByShadowUserAuthUserIdAndLegalEntityId(
            UUID authUserId, UUID legalEntityId);

    /** LLR-FIN-01.3: used to update lastAccessedAt on context switch. */
    @Modifying
    @Query("""
           UPDATE EntityUserAccess eua
           SET eua.lastAccessedAt = :accessedAt
           WHERE eua.shadowUser.authUserId = :authUserId
             AND eua.legalEntity.id = :entityId
           """)
    void updateLastAccessedAt(
             @Param("authUserId") UUID authUserId,
             @Param("entityId") UUID entityId,
             @Param("accessedAt") LocalDateTime accessedAt);

    /**
     * Activates a PENDING access grant when the user first switches to
     * this entity context. Transitions status from PENDING to ACTIVE.
     */
    @Modifying
    @Query("""
           UPDATE EntityUserAccess eua
           SET eua.status = :status
           WHERE eua.shadowUser.authUserId = :authUserId
             AND eua.legalEntity.id = :entityId
           """)
    void updateStatusByAuthUserIdAndLegalEntityId(
             @Param("status") Status status,
             @Param("authUserId") UUID authUserId,
             @Param("entityId") UUID entityId);
}
