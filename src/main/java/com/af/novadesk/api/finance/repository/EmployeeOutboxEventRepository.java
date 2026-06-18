package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.EmployeeOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link EmployeeOutboxEvent}.
 *
 * <p>Used internally by the Employee module to persist outbox events.
 * Cross-module consumers should read this table via raw SQL/JdbcTemplate
 * to avoid a JPA dependency on this class.</p>
 */
@Repository
public interface EmployeeOutboxEventRepository extends JpaRepository<EmployeeOutboxEvent, UUID> {

    Optional<EmployeeOutboxEvent> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Fetches a batch of deliverable outbox events (PENDING or FAILED with
     * {@code nextRetryAt} in the past), ordered by creation time for FIFO delivery.
     *
     * @param status   the outbox status to filter by (typically PENDING)
     * @param pageable pagination (batch size control)
     * @return ordered list of outbox events ready for delivery
     */
    List<EmployeeOutboxEvent> findByOutboxStatusOrderByCreatedAtAsc(
            OutboxEventStatus status, Pageable pageable);
}
