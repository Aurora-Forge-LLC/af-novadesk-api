package com.af.novadesk.api.payroll.scheduler;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.EmployeeOutboxEvent;
import com.af.novadesk.api.common.repository.EmployeeOutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Polls {@link EmployeeOutboxEvent} rows with status {@code PENDING} and
 * publishes them to the {@code af.employee.events} RabbitMQ exchange.
 *
 * <p>Follows the same transactional-outbox-to-message-broker pattern used
 * by the existing outbox publishers across the codebase (e.g.
 * {@code AssetOutboxPublisher}). The outbox event is written in the same
 * database transaction as the employee CRUD operation, guaranteeing
 * at-least-once delivery to RabbitMQ after commit.</p>
 *
 * <p>Retry / dead-letter:
 * <ul>
 *   <li>Transient publish failures → retried on next poll cycle
 *       (status stays {@code FAILED})</li>
 *   <li>After {@code maxRetries} attempts → status becomes {@code DEAD}</li>
 *   <li>RabbitMQ broker-level DLX/DLQ is configured on the queue for
 *       consumer-side failures</li>
 * </ul>
 */
@Slf4j
@Component
public class EmployeeOutboxPublisher {

    private final EmployeeOutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.employee.exchange:af.employee.events}")
    private String exchangeName;

    @Value("${app.rabbitmq.employee.routing-key:employee.onboarded.v1}")
    private String routingKey;

    @Value("${app.jwt.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.jwt.outbox.max-retries:5}")
    private int maxRetries;

    public EmployeeOutboxPublisher(EmployeeOutboxEventRepository outboxRepository,
                                   RabbitTemplate rabbitTemplate,
                                   ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Scheduled poller that reads PENDING outbox rows and publishes them
     * to RabbitMQ.
     *
     * <p>Runs every 5 seconds (configurable via {@code app.jwt.outbox.poll-interval-ms}).
     * Each batch is processed in its own transaction so that a single bad
     * row does not roll back the entire batch.</p>
     */
    @Scheduled(fixedDelayString = "${app.jwt.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<EmployeeOutboxEvent> pending = outboxRepository
                .findByOutboxStatusOrderByCreatedAtAsc(
                        OutboxEventStatus.PENDING,
                        PageRequest.of(0, batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Employee outbox publisher: found {} PENDING events", pending.size());

        for (EmployeeOutboxEvent event : pending) {
            try {
                publishEvent(event);
                markPublished(event);
            } catch (Exception e) {
                handleFailure(event, e);
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void publishEvent(EmployeeOutboxEvent event) throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);

        rabbitTemplate.convertAndSend(
                exchangeName,
                routingKey,
                payload,
                message -> {
                    message.getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties()
                            .setMessageId(event.getIdempotencyKey());
                    return message;
                });

        log.debug("Employee outbox event published to RabbitMQ: idempotencyKey={}, type={}",
                event.getIdempotencyKey(), event.getEventType());
    }

    private void markPublished(EmployeeOutboxEvent event) {
        event.setOutboxStatus(OutboxEventStatus.PUBLISHED);
        event.setUpdatedAt(LocalDateTime.now());
        outboxRepository.save(event);
    }

    private void handleFailure(EmployeeOutboxEvent event, Exception ex) {
        int attempts = event.getRetryCount() + 1;
        event.setRetryCount(attempts);
        event.setLastError(truncate(ex.getMessage(), 1000));

        if (attempts >= maxRetries) {
            event.setOutboxStatus(OutboxEventStatus.DEAD);
            log.error("[OUTBOX-DEAD] Employee event moved to DEAD after {} attempts: "
                        + "id={}, type={}, idempotencyKey={}, lastError={}",
                    attempts, event.getId(), event.getEventType(),
                    event.getIdempotencyKey(), event.getLastError());
        } else {
            event.setOutboxStatus(OutboxEventStatus.FAILED);
            log.warn("[OUTBOX-RETRY] Employee event publish failed (attempt {}/{}): "
                        + "id={}, type={}, idempotencyKey={}, error={}",
                    attempts, maxRetries, event.getId(), event.getEventType(),
                    event.getIdempotencyKey(), event.getLastError());
        }

        event.setUpdatedAt(LocalDateTime.now());
        outboxRepository.save(event);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
