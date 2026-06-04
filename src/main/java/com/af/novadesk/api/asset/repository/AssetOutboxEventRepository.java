package com.af.novadesk.api.asset.repository;

import com.af.novadesk.api.asset.entity.AssetOutboxEvent;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link AssetOutboxEvent}.
 *
 * <p>The polling query uses {@code FOR UPDATE SKIP LOCKED} to prevent
 * concurrent pollers from picking up the same row — safe for multi-instance
 * deployments without a distributed lock.</p>
 */
@Repository
public interface AssetOutboxEventRepository extends JpaRepository<AssetOutboxEvent, UUID> {

    /**
     * Fetches the next batch of deliverable outbox events.
     * Rows are eligible if they are PENDING or FAILED and either have no
     * {@code nextRetryAt} (first attempt) or their retry window has elapsed.
     *
     * <p>Uses {@code FOR UPDATE SKIP LOCKED} to prevent duplicate processing
     * across concurrent poller instances.</p>
     */
    @Query(value = """
            SELECT * FROM af_novadesk_outbox.ast_outbox_events
             WHERE outbox_event_status IN ('PENDING', 'FAILED')
               AND (next_retry_at IS NULL OR next_retry_at <= :now)
             ORDER BY created_at
             FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<AssetOutboxEvent> findDeliverableBatch(
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Bulk-marks a batch of events as PUBLISHED after successful delivery.
     */
    @Modifying
    @Query("""
            UPDATE AssetOutboxEvent e
               SET e.outboxEventStatus = :status,
                   e.publishedAt       = :publishedAt
             WHERE e.id IN :ids
            """)
    void markPublished(
            @Param("ids")         List<UUID>        ids,
            @Param("status")      OutboxEventStatus status,
            @Param("publishedAt") Instant           publishedAt);

    /**
     * Counts unprocessed events — useful for monitoring / actuator exposure.
     */
    long countByOutboxEventStatus(OutboxEventStatus status);
}
