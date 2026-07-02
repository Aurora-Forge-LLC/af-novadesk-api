package com.af.novadesk.api.identity.repository;

import com.af.novadesk.api.identity.entity.ShadowUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@link ShadowUser} entity.
 */
@Repository
public interface ShadowUserRepository extends JpaRepository<ShadowUser, UUID> {

    Optional<ShadowUser> findByAuthUserId(UUID authUserId);

    boolean existsByAuthUserId(UUID authUserId);

    List<ShadowUser> findAllByOrganizationId(UUID organizationId);

    Optional<ShadowUser> findByEmail(String email);

    /**
     * Atomic upsert using PostgreSQL {@code INSERT ... ON CONFLICT DO UPDATE}.
     * Eliminates the race condition in the check-then-insert pattern where
     * two concurrent requests for the same {@code authUserId} both see an
     * empty result and attempt to INSERT.
     *
     * <p>On INSERT: {@code created_at} and {@code updated_at} are both set
     * to the current transaction timestamp (same value — used to detect INSERT vs UPDATE).
     * On UPDATE: only {@code updated_at} and {@code last_synced_at} are refreshed.</p>
     *
     * @param authUserId  JWT {@code sub} claim — the canonical cross-service identity
     * @param orgId       JWT {@code organizationId} claim
     * @param email       JWT {@code email} claim
     * @param displayName JWT {@code name} claim (may be null)
     */
    @Modifying
    @Query(value = """
        INSERT INTO af_novadesk.shadow_users
            (id, auth_user_id, organization_id, email, display_name, last_synced_at, status, created_at, updated_at)
        VALUES (:id, :authUserId, :orgId, :email, :displayName, NOW(), 'ACTIVE', NOW(), NOW())
        ON CONFLICT (auth_user_id) DO UPDATE SET
            email = EXCLUDED.email,
            display_name = COALESCE(EXCLUDED.display_name, shadow_users.display_name),
            last_synced_at = NOW(),
            updated_at = NOW()
        """, nativeQuery = true)
    int upsertShadowUser(@Param("id") UUID id, @Param("authUserId") UUID authUserId,
                         @Param("orgId") UUID orgId,
                         @Param("email") String email, @Param("displayName") String displayName);

    /**
     * Permanently deletes the shadow user record associated with the given AuthHub user ID.
     * Used during the employee hard-delete flow.
     */
    void deleteByAuthUserId(UUID authUserId);
}