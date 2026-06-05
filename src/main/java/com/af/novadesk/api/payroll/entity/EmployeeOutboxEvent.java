package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional outbox for employee lifecycle events.
 *
 * <p>One row per event (EMPLOYEE_ONBOARDED, EMPLOYEE_UPDATED, EMPLOYEE_TERMINATED).
 * Written in the same {@code @Transactional} as the Employee CRUD operation.
 * Cross-module consumers (e.g. Finance's EntityAccessSyncService) read this table
 * via raw SQL ({@code JdbcTemplate}) — no JPA import of this class.
 *
 * <p>The {@code idempotencyKey} has a UNIQUE constraint to guarantee
 * at-least-once delivery. Consumers can safely retry without double-processing.
 */
@Entity
@Table(name = "pr_employee_outbox_events", schema = "af_novadesk",
       indexes = {
           @Index(columnList = "outbox_status, created_at", name = "idx_emp_outbox_status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull
    private String eventType;

    @Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
    @NotNull
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "JSONB", nullable = false)
    @NotNull
    private String payload;

    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull
    private UUID organizationId;

    @Column(name = "employee_id", nullable = false, columnDefinition = "UUID")
    @NotNull
    private UUID employeeId;

    @Column(name = "auth_user_id", nullable = false, columnDefinition = "UUID")
    @NotNull
    private UUID authUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbox_status", nullable = false, length = 30)
    @Builder.Default
    private OutboxEventStatus outboxStatus = OutboxEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
