package com.af.novadesk.api.payroll.outbox;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Polling publisher for the Employee module transactional outbox.
 *
 * <p>Runs on a fixed-delay schedule, fetches PENDING or retryable
 * FAILED rows from {@code employee_outbox_events}, publishes them
 * to RabbitMQ for AuthHub consumption, and marks them PUBLISHED
 * on success.</p>
 *
 * <p>Concurrency safety: uses {@code FOR UPDATE SKIP LOCKED} via
 * {@link EmployeeOutboxEventRepository#findDeliverableBatch} —
 * multiple poller instances can run safely.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeOutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(60);

    private static final String EXCHANGE = "employee-events";
    private static final String ROUTING_KEY_PREFIX = "employee.";

    private final EmployeeOutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${app.jwt.outbox.retry-backoff-seconds:30}000")
    @Transactional
    public void poll() {
        Instant now = Instant.now();
        List<EmployeeOutboxEvent> batch = outboxRepository.findDeliverableBatch(
                now, PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) return;

        log.debug("Employee outbox poller — processing {} events", batch.size());

        List<UUID> published = new ArrayList<>();

        for (EmployeeOutboxEvent event : batch) {
            try {
                deliver(event);
                published.add(event.getId());
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }

        if (!published.isEmpty()) {
            outboxRepository.markPublished(published, OutboxEventStatus.PUBLISHED, Instant.now());
            log.info("Employee outbox: published {} events", published.size());
        }
    }

    private void deliver(EmployeeOutboxEvent event) {
        String routingKey = ROUTING_KEY_PREFIX
                + event.getEventType().name().toLowerCase().replace('_', '.');

        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, event.getPayload(),
                message -> {
                    message.getMessageProperties().setMessageId(event.getId().toString());
                    message.getMessageProperties().getHeaders()
                            .put("event_type", event.getEventType().name());
                    message.getMessageProperties().getHeaders()
                            .put("organization_id", event.getOrganizationId().toString());
                    return message;
                });

        log.info("[OUTBOX] Employee event published: type={} employeeId={} org={}",
                event.getEventType(), event.getEmployee().getId(), event.getOrganizationId());
    }

    private void handleFailure(EmployeeOutboxEvent event, Exception ex) {
        int attempts = event.getRetryCount() + 1;
        event.setRetryCount(attempts);
        event.setLastError(truncate(ex.getMessage(), 990));

        if (attempts >= MAX_RETRIES) {
            event.setOutboxEventStatus(OutboxEventStatus.DEAD);
            log.error("[OUTBOX] Employee event DEAD after {} attempts: type={} id={}",
                    attempts, event.getEventType(), event.getId(), ex);
        } else {
            event.setOutboxEventStatus(OutboxEventStatus.FAILED);
            event.setNextRetryAt(Instant.now().plus(backoff(attempts)));
            log.warn("[OUTBOX] Employee event failed (attempt {}/{}): type={} id={} — retry at {}",
                    attempts, MAX_RETRIES, event.getEventType(),
                    event.getId(), event.getNextRetryAt(), ex);
        }

        outboxRepository.save(event);
    }

    private Duration backoff(int attempt) {
        long minutes = (long) Math.pow(2, attempt);
        return Duration.ofMinutes(Math.min(minutes, MAX_BACKOFF.toMinutes()));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "Unknown error";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
