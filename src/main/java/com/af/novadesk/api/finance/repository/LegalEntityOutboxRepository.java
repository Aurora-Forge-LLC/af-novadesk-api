package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.LegalEntityOutboxEvent;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link LegalEntityOutboxEvent} entity.
 */
@Repository
public interface LegalEntityOutboxRepository extends JpaRepository<LegalEntityOutboxEvent, UUID> {

    /**
     * Polling query: fetch a batch of deliverable PENDING events ordered by
     * insertion time. Pair with {@code FOR UPDATE SKIP LOCKED} at the
     * JDBC/transaction level in the poller service.
     */
    @Query("""
           SELECT e FROM LegalEntityOutboxEvent e
           WHERE e.outboxEventStatus = :status
             AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
           ORDER BY e.createdAt ASC
           """)
    List<LegalEntityOutboxEvent> findDeliverable(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
