package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.AssetEventType;
import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.AbstractEntity;
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
 * Transactional Outbox for Asset module domain events.
 *
 * <p>Every business operation (register, assign, return, write-off, depreciation)
 * persists one row here <em>inside the same DB transaction</em> as the aggregate
 * change. The polling publisher ({@link com.af.novadesk.api.asset.scheduler.AssetOutboxPublisher})
 * reads {@code PENDING} rows and delivers them to downstream consumers after
 * commit — guaranteeing at-least-once delivery without distributed transactions.</p>
 *
 * <h2>Polling query</h2>
 * <pre>{@code
 * SELECT * FROM af_novadesk_outbox.ast_outbox_events
 *  WHERE outbox_event_status = 'PENDING'
 *    AND (next_retry_at IS NULL OR next_retry_at <= now())
 *  ORDER BY created_at
 *  FOR UPDATE SKIP LOCKED
 *  LIMIT :batchSize
 * }</pre>
 *
 * <h2>Aggregate reference</h2>
 * <p>Uses a loose UUID reference (no FK) because events span multiple
 * aggregate types — {@code Asset}, {@code AssetAssignment}, {@code AssetWriteOff}.
 * The {@code aggregateType} field identifies which entity the {@code aggregateId}
 * belongs to.</p>
 *
 * <h2>Idempotency key convention</h2>
 * {@code "<AssetEventType>:<aggregateId>:<UUID>"}
 */
@Entity
@Table(
    name   = "ast_outbox_events",
    schema = "af_novadesk_outbox",
    indexes = {
        @Index(columnList = "outbox_event_status, created_at",  name = "idx_ast_outbox_status_created"),
        @Index(columnList = "outbox_event_status, next_retry_at", name = "idx_ast_outbox_next_retry"),
        @Index(columnList = "aggregate_id",                      name = "idx_ast_outbox_aggregate"),
        @Index(columnList = "organization_id",                   name = "idx_ast_outbox_org"),
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "idempotency_key", name = "uk_ast_outbox_idempotency_key")
    }
)
@AttributeOverride(name = "status", column = @Column(name = "status", insertable = false, updatable = false))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssetOutboxEvent extends AbstractEntity {

    // ── Aggregate reference ───────────────────────────────────────────────────

    /** UUID of the source aggregate. Loose reference — no FK. May be null for system events. */
    @Column(name = "aggregate_id", columnDefinition = "UUID")
    private UUID aggregateId;

    /** Discriminator for the aggregate type: ASSET, ASSET_ASSIGNMENT, ASSET_WRITE_OFF. */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    @NotBlank
    private String aggregateType;

    // ── Event routing ─────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    @NotNull
    private AssetEventType eventType;

    // ── Payload ───────────────────────────────────────────────────────────────

    /** JSON-serialised event payload stored as {@code jsonb}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank
    private String payload;

    // ── Multi-tenancy context ─────────────────────────────────────────────────

    @Column(name = "organization_id", columnDefinition = "UUID")
    private UUID organizationId;

    /** JWT {@code sub} of the user who triggered this event. Null for scheduler events. */
    @Column(name = "triggered_by_auth_user_id", columnDefinition = "UUID")
    private UUID triggeredByAuthUserId;

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    @NotBlank
    private String idempotencyKey;

    // ── Delivery lifecycle ────────────────────────────────────────────────────

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "outbox_event_status", nullable = false, length = 20)
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
