package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.constants.CapitalInjectionEventType;
import com.af.novadesk.api.finance.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.dto.CapitalInjectionOutboxPayload;
import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.CapitalInjectionOutboxEvent;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repository.CapitalInjectionOutboxEventRepository;
import com.af.novadesk.api.finance.service.CapitalInjectionOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Default implementation of {@link CapitalInjectionOutboxService}.
 * Writes outbox event rows inside the caller's active transaction (LLR-FIN-02).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapitalInjectionOutboxServiceImpl implements CapitalInjectionOutboxService {

    private final CapitalInjectionOutboxEventRepository outboxRepository;
    private final ObjectMapper                          objectMapper;

    @Override
    @Transactional
    public void publishCapitalInjectionCreated(
            CapitalInjection saved,
            UUID journalId,
            LegalEntity targetEntity,
            LegalEntity sourceEntity,
            String callerIdentity
    ) {
        String idempotencyKey = CapitalInjectionEventType.CAPITAL_INJECTION_CREATED
                + ":" + saved.getId()
                + ":" + journalId;

        String payload = buildPayload(saved, journalId, targetEntity, sourceEntity, callerIdentity);

        CapitalInjectionOutboxEvent event = CapitalInjectionOutboxEvent.builder()
                .capitalInjection(saved)
                .eventType(CapitalInjectionEventType.CAPITAL_INJECTION_CREATED)
                .payload(payload)
                .organizationId(targetEntity.getOrganizationId())
                .idempotencyKey(idempotencyKey)
                .triggeredByAuthUserId(parseAuthUserId(callerIdentity))
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .build();

        outboxRepository.save(event);
        log.debug("Outbox event persisted: type={}, injection={}, journal={}",
                CapitalInjectionEventType.CAPITAL_INJECTION_CREATED, saved.getId(), journalId);
    }

    // -------------------------------------------------------------------------

    private String buildPayload(
            CapitalInjection saved,
            UUID journalId,
            LegalEntity targetEntity,
            LegalEntity sourceEntity,
            String callerIdentity
    ) {
        CapitalInjectionOutboxPayload payload = new CapitalInjectionOutboxPayload(
                saved.getId().toString(),
                journalId.toString(),
                saved.getTransferId() != null ? saved.getTransferId().toString() : null,
                targetEntity.getEntityCode(),
                sourceEntity != null ? sourceEntity.getEntityCode() : null,
                saved.getFundingSource().name(),
                saved.getAmountLocal(),
                saved.getCurrencyLocal().trim(),
                saved.getAmountUsd(),
                saved.getExchangeRateUsed(),
                saved.getRateDateUsed().toString(),
                saved.getRateSource().name(),
                saved.getFundingDate().toString(),
                callerIdentity
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new com.af.novadesk.api.finance.exception.OutboxPublishException(
                    CapitalInjectionEventType.CAPITAL_INJECTION_CREATED.name(), saved.getId(), ex);
        }
    }

    private static UUID parseAuthUserId(String callerIdentity) {
        if (callerIdentity == null || callerIdentity.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(callerIdentity.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
