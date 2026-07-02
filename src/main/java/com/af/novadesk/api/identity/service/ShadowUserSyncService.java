package com.af.novadesk.api.identity.service;

import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.repository.ShadowUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps {@link ShadowUser} records in sync with AuthHub JWT claims.
 *
 * <p>Called by the JWT request filter on every authenticated request.
 * Uses an upsert strategy:
 * <ul>
 *   <li>First request from a user → INSERT shadow record, publish {@code SHADOW_USER_CREATED}</li>
 *   <li>Subsequent requests, claims unchanged → no write (read-only fast path)</li>
 *   <li>Subsequent requests, email or name changed → UPDATE + publish {@code SHADOW_USER_UPDATED}</li>
 * </ul>
 * No AuthHub network call is made — all data comes from the already-verified JWT.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowUserSyncService {

    private final ShadowUserRepository  shadowUserRepository;
    private final ShadowUserOutboxService outboxService;

    /**
     * Upserts a shadow user from the provided JWT claim values.
     * Runs in its own transaction so a sync failure does not roll back
     * the surrounding business transaction.
     *
     * @param authUserId     JWT {@code sub} claim
     * @param organizationId JWT {@code organizationId} claim
     * @param email          JWT {@code email} claim
     * @param displayName    JWT {@code name} claim (may be null)
     * @return the resolved ShadowUser (always non-null after this call)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShadowUser upsert(UUID authUserId, UUID organizationId,
                             String email, String displayName) {
        // Snapshot existing values BEFORE the upsert for change detection.
        // This snapshot is advisory only — the native upsert below is the
        // atomic gate that eliminates the race condition.
        Optional<ShadowUser> existingBefore = shadowUserRepository.findByAuthUserId(authUserId);

        // Atomic upsert: INSERT ... ON CONFLICT DO UPDATE.
        // Eliminates the race condition where two concurrent requests for the
        // same authUserId both see an empty result and attempt to INSERT.
        shadowUserRepository.upsertShadowUser(UUID.randomUUID(), authUserId, organizationId, email, displayName);

        // Fetch the persisted entity after the upsert.
        ShadowUser user = shadowUserRepository.findByAuthUserId(authUserId).orElseThrow(
                () -> new IllegalStateException("ShadowUser not found after upsert: " + authUserId));

        // created_at == updated_at → fresh INSERT (both set to the same NOW() in the statement).
        // created_at != updated_at → UPDATE on an existing row.
        // Null-safe: createdAt may be null in test environments or edge cases.
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime updatedAt = user.getUpdatedAt();
        boolean isInsert = createdAt != null && createdAt.equals(updatedAt);

        if (isInsert) {
            log.info("ShadowUser created for authUserId={}", authUserId);
            outboxService.publishShadowUserCreated(user, organizationId, authUserId);
        } else if (existingBefore.isPresent()) {
            ShadowUser old = existingBefore.get();
            if (hasClaimsChanged(old, email, displayName)) {
                log.info("ShadowUser updated for authUserId={}", authUserId);
                outboxService.publishShadowUserUpdated(user, old.getEmail(),
                        organizationId, authUserId);
            }
        }
        // else: UPDATE but pre-upsert snapshot was empty (rare race).
        // Skip the UPDATED outbox event — values will sync on next request.

        return user;
    }

    // -------------------------------------------------------------------------

    private boolean hasClaimsChanged(ShadowUser user, String email, String displayName) {
        boolean emailChanged = !user.getEmail().equals(email);
        boolean nameChanged  = !java.util.Objects.equals(user.getDisplayName(), displayName);
        return emailChanged || nameChanged;
    }
}