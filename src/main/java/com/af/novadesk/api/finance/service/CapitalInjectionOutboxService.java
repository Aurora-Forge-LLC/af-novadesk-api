package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.CapitalInjectionEventType;
import com.af.novadesk.api.finance.dto.CapitalInjectionOutboxPayload;
import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.CapitalInjectionOutboxEvent;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.finance.repository.CapitalInjectionOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Transactional Outbox publisher for the {@link CapitalInjection} aggregate
 * (LLR-FIN-02).
 *
 * <p>Writes {@link CapitalInjectionOutboxEvent} rows to the outbox table
 * inside the caller's active transaction.  This service mirrors the pattern
 * established by {@link LegalEntityOutboxService} and
 * {@link EntityUserAccessOutboxService} for modular-monolith consistency.</p>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<CapitalInjectionEventType>:<capitalInjectionId>:<journalId>"}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CapitalInjectionOutboxService {

    private final CapitalInjectionOutboxEventRepository outboxRepository;
    private final ObjectMapper                          objectMapper;

    /**
     * Persists a {@code CAPITAL_INJECTION_CREATED} outbox event in the current
     * transaction.  The polling publisher will deliver the JSON payload to the
     * downstream message broker after the enclosing transaction commits.
     *
     * @param saved         the persisted capital-injection header
     * @param journalId     UUID that groups the double-entry ledger lines
     * @param targetEntity  the entity receiving the capital
     * @param sourceEntity  the funding entity (null for external sources)
     * @param callerIdentity  JWT {@code sub} of the authenticated user
     * @throws IllegalStateException if JSON serialisation fails
     */
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
                .organizationId(targetEntity.getId())
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
    // Payload serialisation
    // -------------------------------------------------------------------------

    /**
     * Serialises the outbox payload via Jackson using the typed
     * {@link CapitalInjectionOutboxPayload} record so that all free-text fields
     * ({@code createdBy}, {@code notes}) are properly escaped — eliminating
     * the JSON-injection vector that existed in the previous manual string
     * concatenation.
     */
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
            throw new IllegalStateException(
                    "Failed to serialize outbox payload for injection " + saved.getId(), ex);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Maps principal name (JWT sub) to UUID when possible.
     * Returns null when the principal is not UUID-formatted.
     */
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
