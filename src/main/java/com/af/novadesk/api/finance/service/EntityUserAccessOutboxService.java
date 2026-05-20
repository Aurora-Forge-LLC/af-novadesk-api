package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.constants.EntityUserAccessEventType;
import com.af.novadesk.api.finance.entity.EntityUserAccessOutboxEvent;
import com.af.novadesk.api.common.constants.OutboxEventStatus;

import com.af.novadesk.api.common.exception.OutboxPublishException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.af.novadesk.api.finance.repository.EntityUserAccessOutboxRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox publisher for the {@link EntityUserAccess} aggregate.
 * Writes outbox rows inside the caller's active transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityUserAccessOutboxService {

    private final EntityUserAccessOutboxRepository  outboxRepository;
    private final ObjectMapper                     objectMapper;

    public void publishAccessGranted(EntityUserAccess access,
                                     UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "accessId",            access.getId(),
                "entityId",            access.getLegalEntity().getId(),
                "authUserId",          access.getShadowUser().getAuthUserId(),
                "organizationId",      orgId,
                "entityRole",          access.getEntityRole(),
                "grantedByAuthUserId", triggeredBy,
                "grantedAt",           LocalDateTime.now().toString()
        );
        persist(access, EntityUserAccessEventType.USER_ACCESS_GRANTED,
                access.getShadowUser().getAuthUserId(), triggeredBy, orgId, payload);
    }

    public void publishAccessRevoked(EntityUserAccess access,
                                     UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "accessId",             access.getId(),
                "entityId",             access.getLegalEntity().getId(),
                "authUserId",           access.getShadowUser().getAuthUserId(),
                "organizationId",       orgId,
                "entityRole",           access.getEntityRole(),
                "revokedByAuthUserId",  triggeredBy,
                "revokedAt",            LocalDateTime.now().toString()
        );
        persist(access, EntityUserAccessEventType.USER_ACCESS_REVOKED,
                access.getShadowUser().getAuthUserId(), triggeredBy, orgId, payload);
    }

    public void publishRoleChanged(EntityUserAccess access, String previousRole,
                                   String newRole, UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "accessId",            access.getId(),
                "entityId",            access.getLegalEntity().getId(),
                "authUserId",          access.getShadowUser().getAuthUserId(),
                "organizationId",      orgId,
                "previousRole",        previousRole,
                "newRole",             newRole,
                "changedByAuthUserId", triggeredBy
        );
        persist(access, EntityUserAccessEventType.USER_ACCESS_ROLE_CHANGED,
                access.getShadowUser().getAuthUserId(), triggeredBy, orgId, payload);
    }

    private void persist(EntityUserAccess access,
                         EntityUserAccessEventType eventType,
                         UUID affectedAuthUserId,
                         UUID triggeredBy, UUID orgId,
                         Map<String, Object> payloadMap) {
        try {
            String idempotencyKey = eventType.name() + ":" + access.getId() + ":" + UUID.randomUUID();
            EntityUserAccessOutboxEvent event = EntityUserAccessOutboxEvent.builder()
                    .entityUserAccess(access)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payloadMap))
                    .organizationId(orgId)
                    .affectedAuthUserId(affectedAuthUserId)
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxRepository.save(event);
            log.debug("Outbox event persisted: type={}, access={}", eventType, access.getId());
        } catch (JsonProcessingException e) {
            throw new OutboxPublishException(
                    eventType.name(), access.getId(), e);
        }
    }
}