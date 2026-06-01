package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.ExchangeRateEventType;
import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.entity.ExchangeRateOutboxEvent;
import com.af.novadesk.api.finance.repository.ExchangeRateOutboxEventRepository;
import com.af.novadesk.api.finance.service.ExchangeRateOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation of {@link ExchangeRateOutboxService} (LLR-FIN-02.3).
 * Writes outbox event rows inside the caller's active transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateOutboxServiceImpl implements ExchangeRateOutboxService {

    private final ExchangeRateOutboxEventRepository outboxRepository;
    private final ObjectMapper                      objectMapper;

    @Override
    @Transactional
    public void publishSyncCompleted(ExchangeRate rate) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_SYNC_COMPLETED
                + ":" + rate.getRateDate()
                + ":" + rate.getSourceCurrency() + "->" + rate.getTargetCurrency();

        Map<String, Object> payload = Map.of(
                "rateId",         rate.getId().toString(),
                "sourceCurrency", rate.getSourceCurrency(),
                "targetCurrency", rate.getTargetCurrency(),
                "rateDate",       rate.getRateDate().toString(),
                "exchangeRate",   rate.getExchangeRate(),
                "rateSource",     rate.getRateSource().name()
        );

        persist(rate, ExchangeRateEventType.EXCHANGE_RATE_SYNC_COMPLETED,
                payload, idempotencyKey, null);
    }

    @Override
    @Transactional
    public void publishSyncFailed(String sourceCurrency, String targetCurrency,
                                  LocalDate rateDate, String errorMessage, int retryCount) {
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

    @Override
    @Transactional
    public void publishManuallyUpdated(ExchangeRate rate, String updatedBy) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_MANUALLY_UPDATED
                + ":" + rate.getRateDate()
                + ":" + rate.getSourceCurrency() + "->" + rate.getTargetCurrency()
                + ":" + UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "rateId",         rate.getId().toString(),
                "sourceCurrency", rate.getSourceCurrency(),
                "targetCurrency", rate.getTargetCurrency(),
                "rateDate",       rate.getRateDate().toString(),
                "exchangeRate",   rate.getExchangeRate(),
                "rateSource",     rate.getRateSource().name(),
                "updatedBy",      updatedBy != null ? updatedBy : "system"
        );

        persist(rate, ExchangeRateEventType.EXCHANGE_RATE_MANUALLY_UPDATED,
                payload, idempotencyKey, null);
    }

    @Override
    @Transactional
    public void publishCsvImported(CsvUploadResponse summary, String uploadedBy) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_CSV_IMPORTED
                + ":" + LocalDate.now()
                + ":" + UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("totalRows",    summary.totalRows());
        payload.put("successCount", summary.successCount());
        payload.put("skippedCount", summary.skippedCount());
        payload.put("errorCount",   summary.errorCount());
        payload.put("uploadedBy",   uploadedBy != null ? uploadedBy : "system");

        persist(null, ExchangeRateEventType.EXCHANGE_RATE_CSV_IMPORTED,
                payload, idempotencyKey, null);
    }

    @Override
    @Transactional
    public void publishCsvImportFailed(String fileName, String errorMessage, String uploadedBy) {
        String idempotencyKey = ExchangeRateEventType.EXCHANGE_RATE_CSV_IMPORT_FAILED
                + ":" + LocalDate.now()
                + ":" + UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "fileName",   fileName != null ? fileName : "unknown",
                "error",      errorMessage,
                "uploadedBy", uploadedBy != null ? uploadedBy : "system"
        );

        persist(null, ExchangeRateEventType.EXCHANGE_RATE_CSV_IMPORT_FAILED,
                payload, idempotencyKey, null);
    }

    // -------------------------------------------------------------------------

    private void persist(ExchangeRate rate,
                         ExchangeRateEventType eventType,
                         Map<String, Object> payloadMap,
                         String idempotencyKey,
                         UUID triggeredBy) {
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
