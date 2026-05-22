package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.ExchangeRateEventType;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.entity.ExchangeRateOutboxEvent;
import com.af.novadesk.api.finance.repository.ExchangeRateOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional Outbox publisher for the {@link ExchangeRate} aggregate
 * (LLR-FIN-02.3).
 *
 * <p>Writes {@link ExchangeRateOutboxEvent} rows to the outbox table
 * inside the caller's active transaction. This service mirrors the pattern
 * established by {@link CapitalInjectionOutboxService},
 * {@link LegalEntityOutboxService}, and
 * {@link EntityUserAccessOutboxService} for modular-monolith consistency.</p>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<ExchangeRateEventType>:<rateDate>:<sourceCurrency>-><targetCurrency>"}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateOutboxService {

    private final ExchangeRateOutboxEventRepository outboxRepository;
    private final ObjectMapper                      objectMapper;

    /**
     * Persists an {@code EXCHANGE_RATE_SYNC_COMPLETED} outbox event in the
     * current transaction. The polling publisher will deliver the JSON payload
     * to the downstream message broker after the enclosing transaction commits.
     *
     * @param rate the persisted exchange rate record
     */
    @Transactional
    public void publishSyncCompleted(ExchangeRate rate) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_SYNC_COMPLETED
                + ":" + rate.getRateDate()
                + ":" + rate.getSourceCurrency() + "->" + rate.getTargetCurrency();

        Map<String, Object> payload = Map.of(
                "rateId",          rate.getId().toString(),
                "sourceCurrency",  rate.getSourceCurrency(),
                "targetCurrency",  rate.getTargetCurrency(),
                "rateDate",        rate.getRateDate().toString(),
                "exchangeRate",    rate.getExchangeRate(),
                "rateSource",      rate.getRateSource().name()
        );

        persist(rate, ExchangeRateEventType.EXCHANGE_RATE_SYNC_COMPLETED,
                payload, idempotencyKey, null);
    }

    /**
     * Persists an {@code EXCHANGE_RATE_SYNC_FAILED} outbox event in the
     * current transaction. This serves as the Dead Letter Queue record —
     * operators can inspect the error and manually trigger a replay.
     *
     * @param sourceCurrency the source currency code (e.g., "INR")
     * @param targetCurrency the target currency code (e.g., "USD")
     * @param rateDate       the date for which the sync failed
     * @param errorMessage   the exception message from the last failed attempt
     * @param retryCount     the number of retry attempts exhausted
     */
    @Transactional
    public void publishSyncFailed(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate,
            String errorMessage,
            int retryCount
    ) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_SYNC_FAILED
                + ":" + rateDate
                + ":" + sourceCurrency + "->" + targetCurrency
                + ":" + UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "sourceCurrency", sourceCurrency,
                "targetCurrency", targetCurrency,
                "rateDate",       rateDate.toString(),
                "error",          errorMessage,
                "retryCount",     retryCount
        );

        persist(null, ExchangeRateEventType.EXCHANGE_RATE_SYNC_FAILED,
                payload, idempotencyKey, null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void persist(
            ExchangeRate rate,
            ExchangeRateEventType eventType,
            Map<String, Object> payloadMap,
            String idempotencyKey,
            UUID triggeredBy
    ) {
        try {
            String json = objectMapper.writeValueAsString(payloadMap);

            ExchangeRateOutboxEvent event = ExchangeRateOutboxEvent.builder()
                    .exchangeRate(rate)
                    .eventType(eventType)
                    .payload(json)
                    .organizationId(null) // scheduler is system-wide, not org-scoped
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();

            outboxRepository.save(event);
            log.debug("Outbox event persisted: type={}, rateDate={}, pair={}->{}",
                    eventType, rate != null ? rate.getRateDate() : "N/A",
                    payloadMap.get("sourceCurrency"), payloadMap.get("targetCurrency"));

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize outbox payload for exchange rate sync", e);
        }
    }
}
