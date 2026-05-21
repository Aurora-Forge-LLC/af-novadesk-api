package com.af.novadesk.api.identity.service;


import com.af.novadesk.api.common.constants.Status;
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
        Optional<ShadowUser> existing = shadowUserRepository.findByAuthUserId(authUserId);

        if (existing.isEmpty()) {
            return createShadowUser(authUserId, organizationId, email, displayName);
        }

        ShadowUser user = existing.get();
        boolean changed = hasClaimsChanged(user, email, displayName);

        if (changed) {
            String previousEmail = user.getEmail();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setLastSyncedAt(LocalDateTime.now());
            ShadowUser saved = shadowUserRepository.save(user);
            log.info("ShadowUser updated for authUserId={}", authUserId);
            outboxService.publishShadowUserUpdated(saved, previousEmail,
                    organizationId, authUserId);
            return saved;
        }

        return user;
    }

    // -------------------------------------------------------------------------

    private ShadowUser createShadowUser(UUID authUserId, UUID organizationId,
                                        String email, String displayName) {
        ShadowUser user = ShadowUser.builder()
                .authUserId(authUserId)
                .organizationId(organizationId)
                .email(email)
                .displayName(displayName)
                .lastSyncedAt(LocalDateTime.now())
                .status(Status.ACTIVE)
                .build();

        ShadowUser saved = shadowUserRepository.save(user);
        log.info("ShadowUser created for authUserId={}", authUserId);
        outboxService.publishShadowUserCreated(saved, organizationId, authUserId);
        return saved;
    }

    private boolean hasClaimsChanged(ShadowUser user, String email, String displayName) {
        boolean emailChanged = !user.getEmail().equals(email);
        boolean nameChanged  = !java.util.Objects.equals(user.getDisplayName(), displayName);
        return emailChanged || nameChanged;
    }
}