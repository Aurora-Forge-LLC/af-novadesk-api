package com.af.novadesk.api.identity.service;

import com.af.novadesk.api.finance.repository.ShadowUserOutboxRepository;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.identity.entity.ShadowUser;
import com.af.novadesk.api.identity.constants.ShadowUserEventType;
import com.af.novadesk.api.identity.entity.ShadowUserOutboxEvent;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Outbox publisher for the {@link ShadowUser} aggregate.
 * Writes outbox rows inside the caller's active transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowUserOutboxService {

    private final ShadowUserOutboxRepository outboxRepository;
    private final ObjectMapper               objectMapper;

    public void publishShadowUserCreated(ShadowUser user, UUID orgId, UUID triggeredBy) {
        Map<String, Object> payload = Map.of(
                "shadowUserId",  user.getId(),
                "authUserId",    user.getAuthUserId(),
                "organizationId", orgId,
                "email",         user.getEmail(),
                "displayName",   user.getDisplayName() != null ? user.getDisplayName() : "",
                "syncedAt",      user.getLastSyncedAt().toString()
        );
        persist(user, ShadowUserEventType.SHADOW_USER_CREATED, triggeredBy, orgId, payload);
    }

    public void publishShadowUserUpdated(ShadowUser user, String previousEmail,
                                         UUID orgId, UUID triggeredBy) {
        Map<String, Object> payload = Map.of(
                "shadowUserId",  user.getId(),
                "authUserId",    user.getAuthUserId(),
                "organizationId", orgId,
                "previousEmail", previousEmail,
                "newEmail",      user.getEmail(),
                "displayName",   user.getDisplayName() != null ? user.getDisplayName() : "",
                "syncedAt",      user.getLastSyncedAt().toString()
        );
        persist(user, ShadowUserEventType.SHADOW_USER_UPDATED, triggeredBy, orgId, payload);
    }

    private void persist(ShadowUser user,
                         ShadowUserEventType eventType,
                         UUID triggeredBy, UUID orgId,
                         Map<String, Object> payloadMap) {
        try {
            String idempotencyKey = eventType.name() + ":" + user.getId() + ":" + UUID.randomUUID();
            ShadowUserOutboxEvent event = ShadowUserOutboxEvent.builder()
                    .shadowUser(user)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payloadMap))
                    .organizationId(orgId)
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxRepository.save(event);
            log.debug("Outbox event persisted: type={}, user={}", eventType, user.getId());
        } catch (JsonProcessingException e) {
            throw new OutboxPublishException(
                    eventType.name(), user.getId(), e);
        }
    }
}
