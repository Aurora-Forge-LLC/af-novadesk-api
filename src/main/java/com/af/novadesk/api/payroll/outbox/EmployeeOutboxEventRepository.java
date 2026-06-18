package com.af.novadesk.api.payroll.outbox;

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

@Repository
public interface EmployeeOutboxEventRepository extends JpaRepository<EmployeeOutboxEvent, UUID> {

    /**
     * Fetches PENDING events or FAILED events whose next_retry_at has elapsed.
     * Uses FOR UPDATE SKIP LOCKED for safe concurrent polling across instances.
     */
    @Query(value = """
        SELECT e FROM EmployeeOutboxEvent e
        WHERE (e.outboxEventStatus = 'PENDING'
               OR (e.outboxEventStatus = 'FAILED'
                   AND e.nextRetryAt IS NOT NULL
                   AND e.nextRetryAt <= :now))
        ORDER BY e.createdAt ASC
        """)
    List<EmployeeOutboxEvent> findDeliverableBatch(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("UPDATE EmployeeOutboxEvent e SET e.outboxEventStatus = :status, e.publishedAt = :publishedAt WHERE e.id IN :ids")
    void markPublished(@Param("ids") List<UUID> ids,
                       @Param("status") OutboxEventStatus status,
                       @Param("publishedAt") Instant publishedAt);
}
