package com.af.novadesk.api.finance.funding.repository;

import com.af.novadesk.api.finance.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.funding.entity.CapitalInjectionOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link CapitalInjectionOutboxEvent}.
 *
 * <p>Provides the polling query used by the outbox publisher to fetch
 * {@code PENDING} rows in insertion order. The {@code FOR UPDATE SKIP LOCKED}
 * hint prevents multiple poller threads from processing the same row
 * concurrently without blocking each other.</p>
 *
 * <h2>Publisher usage pattern</h2>
 * <pre>{@code
 * @Transactional
 * public void poll() {
 *     List<CapitalInjectionOutboxEvent> batch =
 *         repository.findPendingEvents(LocalDateTime.now(), Pageable.ofSize(100));
 *     for (CapitalInjectionOutboxEvent event : batch) {
 *         try {
 *             broker.publish(event.getPayload());
 *             event.setOutboxEventStatus(OutboxEventStatus.PUBLISHED);
 *             event.setPublishedAt(LocalDateTime.now());
 *         } catch (Exception ex) {
 *             event.setOutboxEventStatus(OutboxEventStatus.FAILED);
 *             event.setRetryCount(event.getRetryCount() + 1);
 *             event.setNextRetryAt(LocalDateTime.now().plusSeconds(...));
 *             event.setLastError(ex.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 */
@Repository
public interface CapitalInjectionOutboxEventRepository
        extends JpaRepository<CapitalInjectionOutboxEvent, UUID> {

    /**
     * Fetches the next batch of deliverable outbox events ordered by creation
     * time (oldest-first FIFO delivery).
     *
     * <p>An event is considered deliverable when:
     * <ul>
     *   <li>Its {@code outboxEventStatus} is {@code PENDING} or {@code FAILED}, and</li>
     *   <li>Its {@code nextRetryAt} is either null (fresh row) or in the past.</li>
     * </ul>
     * </p>
     *
     * <p>The {@code FOR UPDATE SKIP LOCKED} hint ensures safe concurrent polling:
     * two poller instances will never process the same row simultaneously, and
     * neither will block waiting for the other to release the lock.</p>
     *
     * @param now      current timestamp; used to evaluate the {@code nextRetryAt} window
     * @param pageable controls the batch size (use {@link Pageable#ofSize(int)})
     * @return ordered list of deliverable events, at most {@code pageable.getPageSize()} rows
     */
    @Query("""
            SELECT e FROM CapitalInjectionOutboxEvent e
             WHERE e.outboxEventStatus IN ('PENDING', 'FAILED')
               AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
             ORDER BY e.createdAt ASC
            """)
    List<CapitalInjectionOutboxEvent> findPendingEvents(
            @Param("now") LocalDateTime now,
            Pageable pageable);

    /**
     * Counts the total number of events currently in a given delivery state.
     * Useful for health-check endpoints and monitoring dashboards.
     *
     * @param status the {@link OutboxEventStatus} to count
     * @return number of rows in that state
     */
    long countByOutboxEventStatus(OutboxEventStatus status);

    /**
     * Looks up all outbox events for a specific capital injection record.
     * Useful for operational retries and audit queries.
     *
     * @param capitalInjectionId PK of the {@link com.af.novadesk.api.finance.funding.entity.CapitalInjection}
     * @return all outbox events linked to that injection, ordered by creation time
     */
    @Query("""
            SELECT e FROM CapitalInjectionOutboxEvent e
             WHERE e.capitalInjection.id = :capitalInjectionId
             ORDER BY e.createdAt ASC
            """)
    List<CapitalInjectionOutboxEvent> findByCapitalInjectionId(
            @Param("capitalInjectionId") UUID capitalInjectionId);
}

