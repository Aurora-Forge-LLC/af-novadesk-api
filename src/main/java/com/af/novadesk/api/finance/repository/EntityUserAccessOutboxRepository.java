package com.af.novadesk.api.finance.repository;

import com.af.novadesk.api.finance.entity.EntityUserAccessOutboxEvent;
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
 * Repository for the {@link EntityUserAccessOutboxEvent} entity.
 */
@Repository
public interface EntityUserAccessOutboxRepository
        extends JpaRepository<EntityUserAccessOutboxEvent, UUID> {

    @Query("""
           SELECT e FROM EntityUserAccessOutboxEvent e
           WHERE e.outboxEventStatus = :status
             AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)
           ORDER BY e.createdAt ASC
           """)
    List<EntityUserAccessOutboxEvent> findDeliverable(
            @Param("status") OutboxEventStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
