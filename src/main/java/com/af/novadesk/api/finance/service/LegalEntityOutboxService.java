package com.af.novadesk.api.finance.service;


import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.constants.LegalEntityEventType;
import com.af.novadesk.api.finance.entity.LegalEntityOutboxEvent;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.exception.*;
import com.af.novadesk.api.finance.repositories.LegalEntityOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Writes {@link LegalEntityOutboxEvent} rows to the outbox table
 * inside the caller's active transaction.
 *
 * <p>Each method builds a typed payload map, serialises it to JSON,
 * and persists the outbox row. If the JSON serialisation or save fails,
 * an {@link OutboxPublishException} is thrown so the
 * surrounding business transaction rolls back atomically.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalEntityOutboxService {

    private final LegalEntityOutboxRepository outboxRepository;
    private final ObjectMapper                objectMapper;

    public void publishEntityCreated(LegalEntity entity, UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "entityId",              entity.getId(),
                "entityName",            entity.getEntityName(),
                "entityCode",            entity.getEntityCode(),
                "country",               entity.getCountry().name(),
                "baseCurrency",          entity.getBaseCurrency(),
                "incorporationDate",     entity.getIncorporationDate().toString(),
                "submittedByAuthUserId", triggeredBy,
                "organizationId",        orgId
        );
        persist(entity.getId(), LegalEntityEventType.LEGAL_ENTITY_CREATED,
                entity, triggeredBy, orgId, payload);
    }

    public void publishEntityApproved(LegalEntity entity, UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "entityId",            entity.getId(),
                "approvedByAuthUserId", triggeredBy,
                "approvedAt",          java.time.LocalDateTime.now().toString(),
                "organizationId",      orgId
        );
        persist(entity.getId(), LegalEntityEventType.LEGAL_ENTITY_APPROVED,
                entity, triggeredBy, orgId, payload);
    }

    public void publishEntityRejected(LegalEntity entity, String reason,
                                      UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "entityId",             entity.getId(),
                "rejectedByAuthUserId", triggeredBy,
                "reason",               reason,
                "rejectedAt",           java.time.LocalDateTime.now().toString(),
                "organizationId",       orgId
        );
        persist(entity.getId(), LegalEntityEventType.LEGAL_ENTITY_REJECTED,
                entity, triggeredBy, orgId, payload);
    }

    public void publishStatusChanged(LegalEntity entity, Status previous,
                                     Status next, UUID triggeredBy, UUID orgId) {
        Map<String, Object> payload = Map.of(
                "entityId",            entity.getId(),
                "previousStatus",      previous.name(),
                "newStatus",           next.name(),
                "changedByAuthUserId", triggeredBy,
                "organizationId",      orgId
        );
        persist(entity.getId(), LegalEntityEventType.LEGAL_ENTITY_STATUS_CHANGED,
                entity, triggeredBy, orgId, payload);
    }

    // -------------------------------------------------------------------------

    private void persist(UUID aggregateId,
                         LegalEntityEventType eventType,
                         LegalEntity entity,
                         UUID triggeredBy,
                         UUID orgId,
                         Map<String, Object> payloadMap) {
        try {
            String idempotencyKey = eventType.name() + ":" + aggregateId + ":" + UUID.randomUUID();
            String json = objectMapper.writeValueAsString(payloadMap);

            LegalEntityOutboxEvent event = LegalEntityOutboxEvent.builder()
                    .legalEntity(entity)
                    .eventType(eventType)
                    .payload(json)
                    .organizationId(orgId)
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxRepository.save(event);
            log.debug("Outbox event persisted: type={}, aggregate={}", eventType, aggregateId);

        } catch (JsonProcessingException e) {
            throw new OutboxPublishException(eventType.name(), aggregateId, e);
        }
    }
}
