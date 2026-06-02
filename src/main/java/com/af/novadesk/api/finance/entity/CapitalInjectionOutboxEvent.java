package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.CapitalInjectionEventType;
import com.af.novadesk.api.finance.constants.OutboxEventStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox for the {@link CapitalInjection} aggregate (LLR-FIN-02).
 *
 * <p>Every capital injection that is successfully persisted produces exactly one
 * {@code CAPITAL_INJECTION_CREATED} outbox event <em>in the same database
 * transaction</em>.  The polling publisher reads {@code PENDING} rows and
 * delivers them to the message broker after commit, guaranteeing at-least-once
 * delivery without distributed transactions.</p>
 *
 * <h2>Polling query</h2>
 * <pre>{@code
 * SELECT * FROM af_novadesk_outbox.capital_injection_outbox_events
 *  WHERE status = 'PENDING'
 *    AND (next_retry_at IS NULL OR next_retry_at <= now())
 *  ORDER BY created_at
 *  FOR UPDATE SKIP LOCKED
 *  LIMIT :batchSize
 * }</pre>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<CapitalInjectionEventType>:<capitalInjectionId>:<journalId>"}
 *
 * <h2>Retry strategy</h2>
 * <p>The polling publisher increments {@code retryCount} and sets
 * {@code nextRetryAt} using exponential back-off on each failed delivery.
 * After {@code maxRetries} (application-configured), the row is transitioned
 * to {@code DEAD} for manual inspection.</p>
 */
@Entity
@Table(
        name = "capital_injection_outbox_events",
        schema = "af_novadesk_outbox",
        indexes = {
                // Poller's primary scan — ordered by arrival time
                @Index(columnList = "status, created_at",  name = "idx_ci_outbox_status_created"),
                // Look up all events for a specific CapitalInjection
                @Index(columnList = "aggregate_id",        name = "idx_ci_outbox_aggregate_id"),
                // Multi-tenant / entity-scoped debugging
                @Index(columnList = "organization_id",     name = "idx_ci_outbox_org_id"),
                // Efficient retry polling — partial index on retryable states
                @Index(columnList = "status, next_retry_at", name = "idx_ci_outbox_next_retry")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "idempotency_key", name = "uk_ci_outbox_idempotency_key")
        }
)
@AttributeOverride(name = "status",
        column = @Column(name = "outbox_event_status", nullable = false, length = 20))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"capitalInjection"})
public class CapitalInjectionOutboxEvent extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Aggregate Reference
    // -------------------------------------------------------------------------

    /**
     * The {@link CapitalInjection} record whose creation produced this event.
     * FK ensures referential integrity; LAZY fetch since the poller only needs
     * the payload, not the full aggregate graph.
     * <p>Cascade DELETE on the outbox side is intentional: if an injection is
     * hard-deleted (administrative correction), its pending outbox events
     * are also removed.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_outbox_capital_injection"))
    @NotNull(message = "Capital injection aggregate reference is required")
    private CapitalInjection capitalInjection;

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
    private CapitalInjectionEventType eventType;

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    /**
     * JSON-serialised event payload. Stored as {@code jsonb} on PostgreSQL for
     * indexed access. Consumers must treat unknown fields as ignorable
     * (open-closed principle / forward compatibility).
     *
     * <p>Minimum payload structure for {@code CAPITAL_INJECTION_CREATED}:</p>
     * <pre>{@code
     * {
     *   "capitalInjectionId": "<uuid>",
     *   "journalId": "<uuid>",
     *   "transferId": "<uuid|null>",
     *   "targetEntityCode": "INDIA",
     *   "sourceEntityCode": null,
     *   "fundingSource": "FOUNDER_EQUITY",
     *   "amountLocal": 100000.0000,
     *   "currencyLocal": "INR",
     *   "amountUsd": 1200.0000,
     *   "exchangeRateUsed": 0.012000,
     *   "rateDateUsed": "2026-05-18",
     *   "rateSource": "API",
     *   "fundingDate": "2026-05-18",
     *   "createdBy": "finance.admin"
     * }
     * }</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank(message = "Payload is required")
    private String payload;

    // -------------------------------------------------------------------------
    // Multi-Tenancy Context — denormalised for filtering without payload parsing
    // -------------------------------------------------------------------------

    /**
     * ID of the target legal entity receiving the capital injection.
     * Serves as the organizational scope for outbox routing and ops filtering.
     * Denormalised to avoid payload deserialization on every polling cycle.
     */
    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /**
     * JWT {@code sub} of the user whose action triggered this event.
     * Null for system-initiated injections.
     */
    @Column(name = "triggered_by_auth_user_id", columnDefinition = "UUID")
    private UUID triggeredByAuthUserId;

    // -------------------------------------------------------------------------
    // Idempotency
    // -------------------------------------------------------------------------

    /**
     * Producer-assigned idempotency key.
     * Convention: {@code "<CapitalInjectionEventType>:<capitalInjectionId>:<journalId>"}.
     * The unique constraint prevents double-insertion on transaction retry.
     * Consumers use the inherited PK ({@code id}) as their idempotency key.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    // -------------------------------------------------------------------------
    // Delivery Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Current delivery state. Set to {@code PENDING} on insertion by the
     * business transaction; transitioned by the external polling publisher.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    /** Timestamp populated by the poller on successful broker acknowledgement. */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Number of failed delivery attempts. Incremented by the poller on each
     * unsuccessful attempt. Triggers transition to {@code DEAD} after the
     * application-configured {@code maxRetries} threshold.
     */
    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * Earliest time the poller may attempt redelivery.
     * Computed using exponential back-off after each failure.
     * Null for fresh {@code PENDING} rows.
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * Truncated exception message or broker error from the most recent
     * failed delivery attempt. Useful for operational triage.
     */
    @Column(name = "last_error", length = 1000)
    private String lastError;
}

