package com.af.novadesk.api.finance.repository;

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

    List<EntityUserAccess> findAllByShadowUserAuthUserId(UUID authUserId);

    /** LLR-FIN-01.3: duplicate access check before granting. */
    boolean existsByShadowUserAuthUserIdAndLegalEntityId(UUID authUserId, UUID legalEntityId);

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
}
