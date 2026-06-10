package com.af.novadesk.api.common.repository;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.EmployeeOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
