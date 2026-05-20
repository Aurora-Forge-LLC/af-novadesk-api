package com.af.novadesk.api.identity.entity;




import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.identity.constants.ShadowUserEventType;
import com.af.novadesk.api.finance.entity.AbstractEntity;
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
 * Transactional Outbox for the {@link ShadowUser} aggregate.
 *
 * <p>{@code ShadowUser} events are relatively low-volume (created once per user,
 * updated only when JWT claims change) but important for keeping identity
 * projections consistent across Finance sub-modules. Recording them here ensures
 * that a failed downstream sync does not silently leave stale data.</p>
 *
 * <h2>Polling query</h2>
 * <pre>{@code
 * SELECT * FROM shadow_user_outbox_events
 *  WHERE status = 'PENDING'
 *    AND (next_retry_at IS NULL OR next_retry_at <= now())
 *  ORDER BY created_at
 *  FOR UPDATE SKIP LOCKED
 *  LIMIT :batchSize
 * }</pre>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<ShadowUserEventType>:<shadowUserId>:<requestTraceId>"}
 */
@Entity
@Table(
        name = "shadow_user_outbox_events",
        schema = "af_novadesk_outbox",
        indexes = {
                @Index(columnList = "status, created_at",  name = "idx_su_outbox_status_created"),
                @Index(columnList = "aggregate_id",        name = "idx_su_outbox_aggregate_id"),
                @Index(columnList = "organization_id",     name = "idx_su_outbox_org_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "idempotency_key", name = "uk_su_outbox_idempotency_key")
        }
)
@AttributeOverride(name = "status", column = @Column(name = "outbox_event_status", nullable = false, length = 20))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"shadowUser"})
public class ShadowUserOutboxEvent extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Aggregate Reference
    // -------------------------------------------------------------------------

    /**
     * The {@link ShadowUser} record whose upsert produced this event.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_su_outbox_shadow_user"))
    @NotNull(message = "ShadowUser is required")
    private ShadowUser shadowUser;

    // -------------------------------------------------------------------------
    // Event Routing
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull(message = "Event type is required")
    private ShadowUserEventType eventType;

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

    @Column(name = "organization_id", nullable = false, columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /** JWT {@code sub} — same value as {@code shadowUser.authUserId}, denormalised
     *  here so the poller does not need to join to {@code shadow_users} for routing. */
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
