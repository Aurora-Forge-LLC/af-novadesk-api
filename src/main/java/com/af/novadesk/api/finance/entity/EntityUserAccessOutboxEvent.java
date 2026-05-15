package com.af.novadesk.api.finance.entity;




import com.af.novadesk.api.finance.constants.OutboxEventStatus;
import com.af.novadesk.api.finance.constants.EntityUserAccessEventType;
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
 * Transactional Outbox for the {@link EntityUserAccess} aggregate.
 *
 * <p>Access-grant and access-revoke events are security-critical: downstream
 * modules must react to them promptly to enforce entity-scoped data isolation
 * (LLR-FIN-01.3). Recording them in this outbox table — in the same transaction
 * as the {@code EntityUserAccess} row change — guarantees at-least-once delivery
 * even if the broker is temporarily unavailable.</p>
 *
 * <h2>Polling query</h2>
 * <pre>{@code
 * SELECT * FROM entity_user_access_outbox_events
 *  WHERE status = 'PENDING'
 *    AND (next_retry_at IS NULL OR next_retry_at <= now())
 *  ORDER BY created_at
 *  FOR UPDATE SKIP LOCKED
 *  LIMIT :batchSize
 * }</pre>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<EntityUserAccessEventType>:<accessId>:<requestTraceId>"}
 */
@Entity
@Table(
        name = "entity_user_access_outbox_events",
        schema = "af_novadesk_outbox",
        indexes = {
                @Index(columnList = "status, created_at",  name = "idx_eua_outbox_status_created"),
                @Index(columnList = "aggregate_id",        name = "idx_eua_outbox_aggregate_id"),
                @Index(columnList = "organization_id",     name = "idx_eua_outbox_org_id"),
                // Security audits often query by the affected user across all entities
                @Index(columnList = "affected_auth_user_id", name = "idx_eua_outbox_affected_user")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "idempotency_key", name = "uk_eua_outbox_idempotency_key")
        }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"entityUserAccess"})
public class EntityUserAccessOutboxEvent extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Aggregate Reference
    // -------------------------------------------------------------------------

    /**
     * The {@link EntityUserAccess} record whose state change produced this event.
     *
     * <p>Note: for {@code USER_ACCESS_REVOKED} events the referenced record may be
     * soft-deleted (status = INACTIVE) by the time the poller reads this row.
     * The payload carries all fields needed for consumers to act without re-fetching
     * the aggregate.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_eua_outbox_entity_user_access"))
    @NotNull(message = "EntityUserAccess is required")
    private EntityUserAccess entityUserAccess;

    // -------------------------------------------------------------------------
    // Event Routing
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull(message = "Event type is required")
    private EntityUserAccessEventType eventType;

    // -------------------------------------------------------------------------
    // Payload
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank(message = "Payload is required")
    private String payload;

    // -------------------------------------------------------------------------
    // Multi-Tenancy & Security Context  —  denormalised for fast filtering
    // -------------------------------------------------------------------------

    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /**
     * The user whose access was granted, revoked, or changed.
     * Denormalised here (in addition to the payload) so security audit queries
     * can find all access-change events for a user via a simple index scan
     * without parsing JSON.
     */
    @Column(name = "affected_auth_user_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Affected auth user ID is required")
    private UUID affectedAuthUserId;

    /** JWT {@code sub} of the admin who triggered the access change. */
    @Column(name = "triggered_by_auth_user_id", columnDefinition = "UUID")
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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
