package com.af.novadesk.api.asset.scheduler;

import com.af.novadesk.api.asset.entity.AssetOutboxEvent;
import com.af.novadesk.api.asset.repository.AssetOutboxEventRepository;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Polling publisher for the Asset module transactional outbox (LLR-AST-*).
 *
 * <p>Runs on a fixed-delay schedule, fetches the next batch of {@code PENDING}
 * or retryable {@code FAILED} rows, delivers them to downstream consumers,
 * and marks them {@code PUBLISHED} on success or increments {@code retryCount}
 * on failure.</p>
 *
 * <h2>Delivery model</h2>
 * <p>Currently delivers events by logging at INFO level — a stand-in for the
 * future message broker (Kafka, SQS, etc.). Replace the body of
 * {@link #deliver(AssetOutboxEvent)} with the actual broker call when the
 * integration is designed. The retry and dead-letter logic is already wired.</p>
 *
 * <h2>Concurrency safety</h2>
 * <p>Uses {@code FOR UPDATE SKIP LOCKED} in the fetch query — multiple poller
 * instances can run safely without picking up the same row.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetOutboxPublisher {

    private static final int  BATCH_SIZE  = 50;
    private static final int  MAX_RETRIES = 5;

    /** Exponential back-off base: 2^retryCount minutes, capped at 60 min. */
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(60);

    private final AssetOutboxEventRepository outboxRepository;

    /**
     * Main polling loop — runs every 30 seconds with a fixed delay.
     * Fixed delay (not fixed rate) means the next poll only starts after
     * the current batch fully completes, preventing pile-up under load.
     */
    @Scheduled(fixedDelayString = "${app.asset.outbox.poll-interval-ms:30000}")
    @Transactional
    public void poll() {
        Instant now = Instant.now();
        List<AssetOutboxEvent> batch = outboxRepository.findDeliverableBatch(
                now, PageRequest.of(0, BATCH_SIZE));

        if (batch.isEmpty()) return;

        log.debug("Asset outbox poller — processing {} events", batch.size());

        List<UUID> published = new ArrayList<>();

        for (AssetOutboxEvent event : batch) {
            try {
                deliver(event);
                published.add(event.getId());

            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }

        if (!published.isEmpty()) {
            outboxRepository.markPublished(published, OutboxEventStatus.PUBLISHED, Instant.now());
            log.info("Asset outbox: published {} events", published.size());
        }
    }

    // =========================================================================
    // Delivery
    // =========================================================================

    /**
     * Delivers a single outbox event to the downstream consumer.
     *
     * <p>Currently logs the event payload — replace this with a real broker
     * publish call (e.g. {@code kafkaTemplate.send(...)}) once the integration
     * is in place. The surrounding retry / dead-letter logic is already wired
     * and requires no changes when the delivery mechanism changes.</p>
     */
    private void deliver(AssetOutboxEvent event) {
        // TODO: replace with kafkaTemplate.send(...) or SQS publish once broker is wired
        log.info("[OUTBOX] Delivering asset event: type={} aggregate={}:{} org={}",
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getOrganizationId());
        log.debug("[OUTBOX] Payload: {}", event.getPayload());
    }

    // =========================================================================
    // Failure handling
    // =========================================================================

    private void handleFailure(AssetOutboxEvent event, Exception ex) {
        int attempts = event.getRetryCount() + 1;
        event.setRetryCount(attempts);
        event.setLastError(truncate(ex.getMessage(), 990));

        if (attempts >= MAX_RETRIES) {
            event.setOutboxEventStatus(OutboxEventStatus.DEAD);
            log.error("[OUTBOX] Asset event moved to DEAD after {} attempts: type={} id={}",
                    attempts, event.getEventType(), event.getId(), ex);
        } else {
            event.setOutboxEventStatus(OutboxEventStatus.FAILED);
            event.setNextRetryAt(Instant.now().plus(backoff(attempts)));
            log.warn("[OUTBOX] Asset event delivery failed (attempt {}/{}): type={} id={} — retry at {}",
                    attempts, MAX_RETRIES, event.getEventType(), event.getId(), event.getNextRetryAt(), ex);
        }

        outboxRepository.save(event);
    }

    /** Exponential back-off: 2^attempt minutes, capped at MAX_BACKOFF. */
    private Duration backoff(int attempt) {
        long minutes = (long) Math.pow(2, attempt);
        return Duration.ofMinutes(Math.min(minutes, MAX_BACKOFF.toMinutes()));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "Unknown error";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
