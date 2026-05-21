package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.identity.entity.ShadowUserOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for the {@link ShadowUserOutboxEvent} entity.
 */
@Repository
public interface ShadowUserOutboxRepository extends JpaRepository<ShadowUserOutboxEvent, UUID> {

    @Query("""
           SELECT e FROM ShadowUserOutboxEvent e
           WHERE e.outboxEventStatus = :status
             AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
           ORDER BY e.createdAt ASC
           """)
    List<ShadowUserOutboxEvent> findDeliverable(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
