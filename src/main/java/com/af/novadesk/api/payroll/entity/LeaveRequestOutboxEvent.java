package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.payroll.constants.LeaveRequestEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox for the {@link LeaveRequest} aggregate (LLR-PAY-01).
 *
 * <p>Every leave request lifecycle transition produces exactly one outbox
 * event <em>in the same database transaction</em> as the aggregate change.
 * The polling publisher reads {@code PENDING} rows and delivers them to the
 * message broker after commit, guaranteeing at-least-once delivery.</p>
 *
 * <p>Idempotency key convention:
 * {@code "<LeaveRequestEventType>:<leaveRequestId>:<requestTraceId>"}</p>
 */
@Entity
@Table(name = "leave_request_outbox_events", schema = "af_novadesk_outbox",
    indexes = {
        @Index(columnList = "outbox_event_status, created_at", name = "idx_lr_outbox_status_created"),
        @Index(columnList = "aggregate_id", name = "idx_lr_outbox_aggregate_id"),
        @Index(columnList = "organization_id", name = "idx_lr_outbox_org_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "idempotency_key", name = "uk_lr_outbox_idempotency_key")
    })
@AttributeOverride(name = "status",
    column = @Column(name = "record_status", nullable = false, length = 20))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"leaveRequest"})
public class LeaveRequestOutboxEvent extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Aggregate Reference
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lr_outbox_leave_request"))
    @NotNull(message = "Leave request is required")
    private LeaveRequest leaveRequest;

    // -------------------------------------------------------------------------
    // Event Routing
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull(message = "Event type is required")
    private LeaveRequestEventType eventType;

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank(message = "Payload is required")
    private String payload;

    // -------------------------------------------------------------------------
    // Multi-Tenancy Context
    // -------------------------------------------------------------------------

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Column(name = "triggered_by_auth_user_id")
    private UUID triggeredByAuthUserId;

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    // -------------------------------------------------------------------------
    // Delivery Lifecycle
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "outbox_event_status", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    @NotNull
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
