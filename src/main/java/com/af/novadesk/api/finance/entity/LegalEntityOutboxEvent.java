package com.af.novadesk.api.finance.entity;


import com.af.novadesk.api.finance.constants.LegalEntityEventType;
import com.af.novadesk.api.finance.constants.OutboxEventStatus;
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
 * Transactional Outbox for the {@link LegalEntity} aggregate.
 *
 * <p>Every state transition on a {@code LegalEntity} that must produce a
 * side-effect outside the current transaction (notification, downstream sync,
 * CoA seeding trigger) is recorded here <em>in the same database transaction</em>
 * as the aggregate change. The polling publisher reads {@code PENDING} rows and
 * delivers them to the message broker after commit.</p>
 *
 * <h2>Polling query</h2>
 * <pre>{@code
 * SELECT * FROM legal_entity_outbox_events
 *  WHERE status = 'PENDING'
 *    AND (next_retry_at IS NULL OR next_retry_at <= now())
 *  ORDER BY created_at
 *  FOR UPDATE SKIP LOCKED
 *  LIMIT :batchSize
 * }</pre>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<LegalEntityEventType>:<aggregateId>:<requestTraceId>"}
 */
@Entity
@Table(
        name = "legal_entity_outbox_events",
        schema = "af_novadesk_outbox",
        indexes = {
                // Poller's primary query
                @Index(columnList = "status, created_at",  name = "idx_le_outbox_status_created"),
                // Ops: all events for a specific LegalEntity instance
                @Index(columnList = "aggregate_id",        name = "idx_le_outbox_aggregate_id"),
                // Multi-tenant debugging
                @Index(columnList = "organization_id",     name = "idx_le_outbox_org_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "idempotency_key", name = "uk_le_outbox_idempotency_key")
        }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity"})
public class LegalEntityOutboxEvent extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Aggregate Reference
    // -------------------------------------------------------------------------

    /**
     * The {@link LegalEntity} instance whose state change produced this event.
     * FK ensures referential integrity; LAZY fetch since the poller only needs
     * the payload, not the full aggregate.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_le_outbox_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Event Routing
    // -------------------------------------------------------------------------

    /**
     * The specific domain event type. Drives consumer-side handler dispatch
     * and payload deserialization.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull(message = "Event type is required")
    private LegalEntityEventType eventType;

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    /**
     * JSON-serialised event payload. Schema is documented per
     * {@link LegalEntityEventType} value. Stored as {@code jsonb} on PostgreSQL.
     * Consumers must treat unknown fields as ignorable (open-closed principle).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank(message = "Payload is required")
    private String payload;

    // -------------------------------------------------------------------------
    // Multi-Tenancy Context  —  denormalised for filtering without payload parsing
    // -------------------------------------------------------------------------

    /**
     * Organization context in which the event was produced.
     * Denormalised so consumers and ops tooling can filter by org
     * without deserialising the payload JSON.
     */
    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /**
     * JWT {@code sub} of the user whose action triggered this event.
     * Null for system-initiated events (scheduled jobs, auto-seeding).
     */
    @Column(name = "triggered_by_auth_user_id", columnDefinition = "UUID")
    private UUID triggeredByAuthUserId;

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    /**
     * Producer-assigned idempotency key.
     * Convention: {@code "<LegalEntityEventType>:<legalEntityId>:<requestTraceId>"}.
     * Unique constraint prevents double-insertion on transaction retry.
     * Consumers use the inherited PK ({@code id}) as their idempotency key.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    // -------------------------------------------------------------------------
    // Delivery Lifecycle
    // -------------------------------------------------------------------------

    /** Current delivery state. Starts as PENDING on insertion. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    /** Timestamp set by the poller on successful broker acknowledgement. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** Number of failed delivery attempts. Triggers DEAD after max-retries threshold. */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Earliest time the poller may attempt redelivery.
     * Set using exponential back-off after each failed attempt.
     * Null for fresh PENDING rows.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /** Exception message or broker error from the most recent failed attempt. Truncated to 1000 chars. */
    @Column(name = "last_error", length = 1000)
    private String lastError;
}